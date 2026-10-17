package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real PostgreSQL lock waits and natural credential expiry through the MVC security filters. */
@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key=",
    "app.labs.enabled=false", "app.labs.operator-epoch="
})
@AutoConfigureMockMvc
class SecurityClockIntegrationTest {
  static final String TOKEN = "current-session-clock";
  static final String PAUSE_REASON = "Queued administrator clock regression";
  @Autowired JdbcTemplate db;
  @Autowired DataSource source;
  @Autowired MockMvc mvc;
  UUID actor, product, first, second;

  enum Kind { MEDIA_ORDER, MEDIA_REMOVE, ADMIN_PAUSE }

  @BeforeEach
  void fixtures() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE storage_deletions,rate_buckets");
    db.update("UPDATE lab_runtime_settings SET paused=false,pause_reason='',observed_epoch=NULL WHERE id=1");
    actor = UUID.randomUUID();
    product = UUID.randomUUID();
    first = UUID.randomUUID();
    second = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at)"
        + " VALUES(?,'clock@example.test','unused',clock_timestamp())", actor);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'ADMIN')", actor, actor);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status)"
        + " VALUES(?,'clock-builder','Clock builder','APPROVED')", actor);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", actor);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified,issued_at)"
        + " VALUES(?,?,clock_timestamp()+interval '1 hour',true,clock_timestamp())",
        Support.hash(TOKEN), actor);
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,"
        + "category,technology,contribution_text,rights_confirmed) VALUES(?,?,'clock-project',"
        + "'Clock project','A proof project','Synthetic clock regression project','SAAS',"
        + "'Inventory','Java','Built the project',true)", product, actor);
    media(first, 0, "First proof");
    media(second, 1, "Second proof");
  }

  void media(UUID id, int position, String alt) {
    db.update("INSERT INTO product_media(id,product_id,storage_key,thumbnail_key,content_type,"
        + "size_bytes,verified,alt_text,sort_order,width,height)"
        + " VALUES(?,?,?,?,'image/png',100,true,?,?,16,8)",
        id, product, id + ".png", id + "-640.png", alt, position);
  }

  ResultActions mutate(Kind kind) throws Exception {
    MockHttpServletRequestBuilder request;
    if (kind == Kind.MEDIA_ORDER) {
      request = patch("/api/v1/developer/products/" + product + "/media")
          .content("{\"items\":[{\"id\":\"" + second + "\",\"alt\":\"Second reordered\"},"
              + "{\"id\":\"" + first + "\",\"alt\":\"First reordered\"}]}");
    } else if (kind == Kind.MEDIA_REMOVE) {
      request = delete("/api/v1/developer/products/" + product + "/media/" + first);
    } else {
      request = post("/api/v1/admin/labs/pause-admissions")
          .content("{\"paused\":true,\"reason\":\"" + PAUSE_REASON + "\"}");
    }
    return mvc.perform(request.cookie(new Cookie("gl_session", TOKEN))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json"));
  }

  Timestamp armDeadline(Kind kind, boolean expires) {
    if (kind == Kind.ADMIN_PAUSE) {
      // The MFA deadline is issued_at + 15 minutes, independent of the JVM clock.
      db.update("UPDATE sessions SET issued_at=clock_timestamp()-"
          + (expires ? "interval '898 seconds'" : "interval '60 seconds'")
          + " WHERE token_hash=?", Support.hash(TOKEN));
      return db.queryForObject("SELECT issued_at+interval '15 minutes' FROM sessions"
          + " WHERE token_hash=?", Timestamp.class, Support.hash(TOKEN));
    }
    db.update("UPDATE sessions SET expires_at=clock_timestamp()+"
        + (expires ? "interval '6 seconds'" : "interval '1 hour'")
        + " WHERE token_hash=?", Support.hash(TOKEN));
    return db.queryForObject("SELECT expires_at FROM sessions WHERE token_hash=?",
        Timestamp.class, Support.hash(TOKEN));
  }

  List<Map<String, Object>> mediaState() {
    return db.queryForList("SELECT id,storage_key,thumbnail_key,alt_text,sort_order"
        + " FROM product_media WHERE product_id=? ORDER BY sort_order,id", product);
  }

  void queued(Kind kind, boolean expires) throws Exception {
    var originalMedia = mediaState();
    var originalSettings = db.queryForMap("SELECT paused,pause_reason,updated_at"
        + " FROM lab_runtime_settings WHERE id=1");
    int originalAudit = db.queryForObject("SELECT count(*) FROM lab_operator_audit", Integer.class);
    String table = kind == Kind.ADMIN_PAUSE ? "users" : "products";
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = source.getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var row = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(row.next());
        holderPid = row.getInt(1);
      }
      try (var lock = holder.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
        lock.setObject(1, kind == Kind.ADMIN_PAUSE ? actor : product);
        try (var row = lock.executeQuery()) { assertTrue(row.next()); }
      }
      try {
        // No credential, role, account or deadline is changed once the request starts.
        Timestamp deadline = armDeadline(kind, expires);
        var response = executor.submit(() -> mutate(kind));
        long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean blocked = false;
        while (System.nanoTime() < stop) {
          Integer waits = db.queryForObject("SELECT count(*) FROM pg_stat_activity"
              + " WHERE datname=current_database() AND wait_event_type='Lock'"
              + " AND ?=ANY(pg_blocking_pids(pid)) AND xact_start<? AND query LIKE ?",
              Integer.class, holderPid, deadline, "%FROM " + table + "%FOR UPDATE%");
          if (waits != null && waits > 0) { blocked = true; break; }
          LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertTrue(blocked, "Request must start before expiry and queue on the intended row");
        assertTrue(db.queryForObject("SELECT clock_timestamp()<?", Boolean.class, deadline),
            "Credential must still be current when the real lock wait is observed");
        if (expires) {
          db.queryForObject("SELECT pg_sleep(GREATEST(0,extract(epoch FROM"
              + " (?::timestamptz-clock_timestamp()))))", Object.class, deadline);
          assertTrue(db.queryForObject("SELECT clock_timestamp()>=?", Boolean.class, deadline));
        }
        holder.commit();
        var result = response.get(10, TimeUnit.SECONDS);
        if (expires) {
          // In particular, a post-commit 403 from the lab response cannot conceal a mutation.
          assertAll("Expired authority must leave durable state unchanged",
              () -> assertEquals(originalMedia, mediaState()),
              () -> assertEquals(0, db.queryForObject("SELECT count(*) FROM storage_deletions", Integer.class)),
              () -> assertEquals(originalSettings, db.queryForMap("SELECT paused,pause_reason,updated_at"
                  + " FROM lab_runtime_settings WHERE id=1")),
              () -> assertEquals(originalAudit, db.queryForObject("SELECT count(*) FROM lab_operator_audit", Integer.class)),
              () -> result.andExpect(status().is(kind == Kind.ADMIN_PAUSE ? 403 : 401))
                  .andExpect(jsonPath("$.error.code").value(
                      kind == Kind.ADMIN_PAUSE ? "FORBIDDEN" : "UNAUTHENTICATED")));
        } else {
          result.andExpect(status().isOk());
          if (kind == Kind.MEDIA_ORDER) {
            assertEquals(List.of(second, first), db.queryForList("SELECT id FROM product_media"
                + " WHERE product_id=? ORDER BY sort_order", UUID.class, product));
            assertEquals(List.of("Second reordered", "First reordered"), db.queryForList(
                "SELECT alt_text FROM product_media WHERE product_id=? ORDER BY sort_order", String.class, product));
            assertEquals(0, db.queryForObject("SELECT count(*) FROM storage_deletions", Integer.class));
          } else if (kind == Kind.MEDIA_REMOVE) {
            assertEquals(List.of(second), db.queryForList("SELECT id FROM product_media WHERE product_id=?",
                UUID.class, product));
            assertEquals(List.of(first + "-640.png", first + ".png"), db.queryForList(
                "SELECT storage_key FROM storage_deletions ORDER BY storage_key", String.class));
          } else {
            assertEquals(true, db.queryForObject("SELECT paused FROM lab_runtime_settings WHERE id=1", Boolean.class));
            assertEquals(PAUSE_REASON, db.queryForObject("SELECT pause_reason FROM lab_runtime_settings WHERE id=1", String.class));
            assertEquals(originalAudit + 1, db.queryForObject("SELECT count(*) FROM lab_operator_audit", Integer.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM lab_operator_audit"
                + " WHERE actor_id=? AND action='ADMISSIONS_PAUSED' AND reason=?",
                Integer.class, actor, PAUSE_REASON));
          }
        }
      } finally {
        holder.rollback();
      }
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "Queued request must finish");
    }
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Kind.class)
  void authorityExpiringDuringLockWaitCannotMutate(Kind kind) throws Exception {
    queued(kind, true);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Kind.class)
  void authorityStillCurrentAfterLockWaitCanMutate(Kind kind) throws Exception {
    queued(kind, false);
  }
}
