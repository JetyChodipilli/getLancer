package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class InvitationExpiryIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired DataSource source;
  @Autowired MockMvc mvc;
  UUID owner, recipient, workspace, invitation;

  enum Kind { BUSINESS, TEAM_REPLY, TEAM_CONTRACT, TEAM_OPTIONAL_EXPIRY }

  static Stream<Arguments> lockCases() {
    return Stream.of(Kind.values()).flatMap(kind ->
        Stream.of(Arguments.of(kind, true), Arguments.of(kind, false)));
  }

  UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'CLIENT')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status)"
        + " VALUES(?,?,?,'APPROVED')", id, name, name);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(name), id);
    return id;
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("owner");
    recipient = user("recipient");
    workspace = UUID.randomUUID();
    invitation = UUID.randomUUID();
  }

  void fixture(Kind kind, boolean expires) {
    if (kind == Kind.BUSINESS) {
      db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Private business','Confidential workspace',?)",
          workspace, owner);
      db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')",
          workspace, owner);
      db.update("INSERT INTO business_invitations(id,business_id,user_id) VALUES(?,?,?)",
          invitation, workspace, recipient);
    } else {
      db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id)"
          + " VALUES(?,?,'Private team','Confidential team workspace','AVAILABLE_NOW','10k',?)",
          workspace, workspace.toString(), owner);
      db.update("INSERT INTO team_members(team_id,user_id,role,membership_type)"
          + " VALUES(?,?,'OWNER','PERMANENT')", workspace, owner);
      db.update("INSERT INTO team_invitations(id,team_id,user_id,role,membership_type,expires_at)"
          + " VALUES(?,?,?,'RECRUITER',?,?)", invitation, workspace, recipient,
          kind == Kind.TEAM_CONTRACT ? "CONTRACT" : "PERMANENT",
          kind == Kind.TEAM_CONTRACT
              ? db.queryForObject("SELECT clock_timestamp()+interval '1 hour'", Timestamp.class) : null);
    }
    String table = kind == Kind.BUSINESS ? "business_invitations" : "team_invitations";
    String column = kind == Kind.TEAM_REPLY ? "respond_by" : "expires_at";
    // Arm a real deadline before the request starts. No deadline or status changes while it waits.
    db.update("UPDATE " + table + " SET " + column + "=clock_timestamp()+"
        + (expires ? "interval '6 seconds'" : "interval '1 hour'") + " WHERE id=?", invitation);
  }

  org.springframework.test.web.servlet.ResultActions respond(Kind kind) throws Exception {
    String prefix = kind == Kind.BUSINESS ? "business" : "team";
    return mvc.perform(post("/api/v1/" + prefix + "-invitations/" + invitation + "/respond")
        .cookie(new Cookie("gl_session", "recipient"))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content("{\"action\":\"ACCEPT\"}"));
  }

  void queued(Kind kind, boolean parentLock, boolean expires) throws Exception {
    fixture(kind, expires);
    String invitationTable = kind == Kind.BUSINESS ? "business_invitations" : "team_invitations";
    String deadlineColumn = kind == Kind.TEAM_REPLY ? "respond_by" : "expires_at";
    String parentTable = kind == Kind.BUSINESS ? "businesses" : "teams";
    Timestamp deadline = db.queryForObject("SELECT " + deadlineColumn + " FROM " + invitationTable
        + " WHERE id=?", Timestamp.class, invitation);
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = source.getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var row = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(row.next());
        holderPid = row.getInt(1);
      }
      try (var lock = holder.prepareStatement("SELECT id FROM "
          + (parentLock ? parentTable : invitationTable) + " WHERE id=? FOR UPDATE")) {
        lock.setObject(1, parentLock ? workspace : invitation);
        try (var row = lock.executeQuery()) { assertTrue(row.next()); }
      }
      try {
        var response = executor.submit(() -> respond(kind));
        long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean blocked = false;
        while (System.nanoTime() < stop) {
          Integer waits = db.queryForObject("SELECT count(*) FROM pg_stat_activity"
              + " WHERE datname=current_database() AND wait_event_type='Lock'"
              + " AND ?=ANY(pg_blocking_pids(pid)) AND xact_start<?",
              Integer.class, holderPid, deadline);
          if (waits != null && waits > 0) { blocked = true; break; }
          LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertTrue(blocked, "Acceptance must begin before expiry and queue on the intended row");
        if (expires) {
          db.queryForObject("SELECT pg_sleep(GREATEST(0,extract(epoch FROM"
              + " (?::timestamptz-clock_timestamp()))))", Object.class, deadline);
          assertTrue(db.queryForObject("SELECT clock_timestamp()>=?", Boolean.class, deadline));
        }
        holder.commit();
        var result = response.get(10, TimeUnit.SECONDS);
        if (expires) {
          result.andExpect(status().isConflict())
              .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
          assertEquals("PENDING", db.queryForObject(
              "SELECT status FROM " + invitationTable + " WHERE id=?", String.class, invitation));
          assertEquals(0, db.queryForObject("SELECT count(*) FROM "
              + (kind == Kind.BUSINESS ? "business_members WHERE business_id=?" : "team_members WHERE team_id=?")
              + " AND user_id=?", Integer.class, workspace, recipient));
          assertEquals(0, db.queryForObject("SELECT count(*) FROM "
              + (kind == Kind.BUSINESS ? "business_activity WHERE business_id=?" : "team_activity WHERE team_id=?")
              + " AND actor_id=?", Integer.class, workspace, recipient));
        } else {
          result.andExpect(status().isOk());
          assertEquals("ACCEPTED", db.queryForObject(
              "SELECT status FROM " + invitationTable + " WHERE id=?", String.class, invitation));
          assertEquals(kind == Kind.BUSINESS ? "HIRING_MANAGER" : "RECRUITER",
              db.queryForObject("SELECT role FROM "
                  + (kind == Kind.BUSINESS ? "business_members WHERE business_id=?" : "team_members WHERE team_id=?")
                  + " AND user_id=?", String.class, workspace, recipient));
          if (kind != Kind.BUSINESS && kind != Kind.TEAM_REPLY)
            assertEquals(deadline, db.queryForObject("SELECT expires_at FROM team_members"
                + " WHERE team_id=? AND user_id=?", Timestamp.class, workspace, recipient));
        }
      } finally {
        holder.rollback();
      }
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "Queued request must finish");
    }
  }

  @ParameterizedTest(name = "{0}, parent lock={1}")
  @MethodSource("lockCases")
  void expiredWhileWaitingCannotGrantMembership(Kind kind, boolean parentLock) throws Exception {
    queued(kind, parentLock, true);
  }

  @ParameterizedTest(name = "{0}, parent lock={1}")
  @MethodSource("lockCases")
  void validAfterWaitingPreservesMembershipConsent(Kind kind, boolean parentLock) throws Exception {
    queued(kind, parentLock, false);
  }
}
