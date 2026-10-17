package com.getlancer.teams;

import static org.junit.jupiter.api.Assertions.*;

import com.getlancer.business.MatchingService;
import com.getlancer.business.BusinessService;
import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.maintenance.MaintenanceRepository;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
class TeamMembershipExpiryIntegrationTest {
  enum WaitTarget { PARENT, MEMBERSHIP }

  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager transactions;
  @Autowired TeamService teams;
  @Autowired DeliveryRepository delivery;
  @Autowired MaintenanceRepository maintenance;
  @Autowired MatchingService matching;
  @Autowired BusinessService businesses;
  UUID owner, member, buyer, team, business, engagement, product;

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    owner = user("expiry-owner");
    member = user("expiry-member");
    buyer = user("expiry-buyer");
    team = UUID.randomUUID();
    business = UUID.randomUUID();
    engagement = UUID.randomUUID();
    product = UUID.randomUUID();
    UUID brief = UUID.randomUUID();
    db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id) VALUES(?,?, 'Expiry studio','Current team authority','AVAILABLE_NOW','Small projects',?)", team, team.toString(), owner);
    db.update("INSERT INTO team_members(team_id,user_id,role,membership_type) VALUES(?,?,'OWNER','PERMANENT')", team, owner);
    db.update("INSERT INTO team_members(team_id,user_id,role,membership_type,expires_at) VALUES(?,?,'BUSINESS_MANAGER','CONTRACT',clock_timestamp()+interval '1 day')", team, member);
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Expiry business','Private business source',?)", business, buyer);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", business, buyer);
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status) VALUES(?,?,?,'Expiry request','A private project for current team members','CRM','React','10000','One month','OPEN')", brief, business, buyer);
    db.update("INSERT INTO delivery_engagements(id,business_request_id,business_id,team_id,title,created_by) VALUES(?,?,?,?,'Expiry engagement',?)", engagement, brief, business, team, buyer);
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,available_for_similar_work) VALUES(?,?,?,'Expiry proof','Current project proof','A working customer portal','SAAS','CRM','React','Built the portal','APPROVED','ACTIVE','PUBLIC',true)", product, member, product.toString());
    db.update("INSERT INTO team_projects(team_id,product_id,consented_by) VALUES(?,?,?)", team, product, member);
  }

  private UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, id.toString(), name);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  private MockHttpServletRequest request() {
    return request("expiry-member");
  }

  private MockHttpServletRequest request(String actor) {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", actor));
    return request;
  }

  private Map<String, Object> roleBody() {
    return Map.of("title", "React builder", "description", "Build accessible customer software",
        "skills", "React", "contractType", "CONTRACT", "compensationBand", "10000");
  }

  @ParameterizedTest
  @EnumSource(WaitTarget.class)
  void teamActionRejectsMembershipThatExpiresDuringEitherLockWait(WaitTarget wait) throws Exception {
    db.update("UPDATE team_members SET role='RECRUITER' WHERE team_id=? AND user_id=?", team, member);
    var denial = afterLockWait(wait, team, true,
        () -> assertThrows(ApiError.class, () -> teams.createRole(team, roleBody(), request())));
    assertEquals(403, denial.status);
    assertEquals(0, db.queryForObject("SELECT count(*) FROM team_roles", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM team_activity", Integer.class));
  }

  @ParameterizedTest
  @EnumSource(WaitTarget.class)
  void deliveryRejectsSellerThatExpiresDuringEitherLockWait(WaitTarget wait) throws Exception {
    var denial = afterLockWait(wait, engagement, true,
        () -> assertThrows(ApiError.class, () -> delivery.lockEngagement(engagement, request(), "SELLER")));
    assertEquals(404, denial.status);
    assertEquals(0, db.queryForObject("SELECT count(*) FROM delivery_activity", Integer.class));
  }

  @ParameterizedTest
  @EnumSource(WaitTarget.class)
  void maintenanceConsentRejectsSellerThatExpiresDuringEitherLockWait(WaitTarget wait) throws Exception {
    assertFalse(afterLockWait(wait, team, true, () -> {
      maintenance.lockConsentParties(engagement, buyer, buyer, member);
      return maintenance.consentAuthority(engagement, member, "SELLER");
    }));
  }

  @ParameterizedTest
  @EnumSource(WaitTarget.class)
  void unexpiredMembersRetainTheirOwnAuthorityAfterEitherLockWait(WaitTarget wait) throws Exception {
    assertEquals("SELLER", afterLockWait(wait, engagement, false,
        () -> delivery.lockEngagement(engagement, request(), "SELLER").get("side")));
    assertTrue(afterLockWait(wait, team, false, () -> {
      maintenance.lockConsentParties(engagement, buyer, buyer, member);
      return maintenance.consentAuthority(engagement, member, "SELLER");
    }));
    db.update("UPDATE team_members SET role='RECRUITER' WHERE team_id=? AND user_id=?", team, member);
    assertEquals("React builder", afterLockWait(wait, team, false,
        () -> teams.createRole(team, roleBody(), request()).get("title")));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM team_roles", Integer.class));
  }

  @Test
  void waitingDeliverySellerCannotKeepARevokedCommercialRole() throws Exception {
    var denial = afterLockWait(WaitTarget.MEMBERSHIP, engagement, false,
        () -> db.update("UPDATE team_members SET role='MEMBER' WHERE team_id=? AND user_id=?", team, member),
        () -> assertThrows(ApiError.class, () -> delivery.lockEngagement(engagement, request(), "SELLER")));
    assertEquals(404, denial.status);
  }

  @Test
  void matchingProofUsesCurrentMembershipInsideAnOlderTransaction() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      assertNotNull(matching.candidate("TEAM", team));
      Timestamp started = db.queryForObject("SELECT now()", Timestamp.class);
      Timestamp expired = db.queryForObject("UPDATE team_members SET expires_at=clock_timestamp() WHERE team_id=? AND user_id=? RETURNING expires_at", Timestamp.class, team, member);
      assertTrue(expired.after(started), "Fixture expiry must be later than transaction start");
      assertEquals(404, assertThrows(ApiError.class, () -> matching.candidate("TEAM", team)).status);
      assertNotNull(matching.candidate("BUILDER", member));
    });
  }

  @Test
  void teamInvitationRetryExpiresTheOldInvitationAfterParentLockWait() throws Exception {
    db.update("UPDATE team_members SET role='RECRUITER' WHERE team_id=? AND user_id=?", team, member);
    UUID old = UUID.randomUUID();
    Timestamp deadline = db.queryForObject("INSERT INTO team_invitations(id,team_id,user_id,role,membership_type,respond_by) VALUES(?,?,?,'MEMBER','PERMANENT',clock_timestamp()+interval '2 seconds') RETURNING respond_by", Timestamp.class, old, team, buyer);
    var invitation = afterLockWait(WaitTarget.PARENT, team, false,
        () -> until(() -> Boolean.TRUE.equals(db.queryForObject("SELECT clock_timestamp()>?", Boolean.class, deadline)), "Old team invitation must expire during the parent wait"),
        () -> {
          assertTrue(db.queryForObject("SELECT now()<?", Boolean.class, deadline));
          return teams.invite(team, Map.of("userId", buyer, "role", "MEMBER", "membershipType", "PERMANENT", "projectLabel", ""), request());
        });
    assertEquals("PENDING", invitation.get("status"));
    assertEquals("EXPIRED", db.queryForObject("SELECT status FROM team_invitations WHERE id=?", String.class, old));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM team_invitations WHERE status='PENDING'", Integer.class));
  }

  @Test
  void businessInvitationRetryExpiresTheOldInvitationAfterParentLockWait() throws Exception {
    UUID old = UUID.randomUUID();
    Timestamp deadline = db.queryForObject("INSERT INTO business_invitations(id,business_id,user_id,expires_at) VALUES(?,?,?,clock_timestamp()+interval '2 seconds') RETURNING expires_at", Timestamp.class, old, business, owner);
    var invitation = afterLockWait(WaitTarget.PARENT, business, false,
        () -> until(() -> Boolean.TRUE.equals(db.queryForObject("SELECT clock_timestamp()>?", Boolean.class, deadline)), "Old business invitation must expire during the parent wait"),
        () -> {
          assertTrue(db.queryForObject("SELECT now()<?", Boolean.class, deadline));
          return businesses.invite(business, Map.of("email", "expiry-owner@example.test"), request("expiry-buyer"));
        });
    assertEquals("PENDING", invitation.get("status"));
    assertEquals("EXPIRED", db.queryForObject("SELECT status FROM business_invitations WHERE id=?", String.class, old));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM business_invitations WHERE status='PENDING'", Integer.class));
  }

  @Test
  void invitationDisplaysUseWallTimeInsideAnOlderTransaction() {
    Timestamp teamDeadline = db.queryForObject("INSERT INTO team_invitations(id,team_id,user_id,role,membership_type,respond_by) VALUES(?,?,?,'MEMBER','PERMANENT',clock_timestamp()+interval '2 seconds') RETURNING respond_by", Timestamp.class, UUID.randomUUID(), team, buyer);
    Timestamp businessDeadline = db.queryForObject("INSERT INTO business_invitations(id,business_id,user_id,expires_at) VALUES(?,?,?,clock_timestamp()+interval '2 seconds') RETURNING expires_at", Timestamp.class, UUID.randomUUID(), business, member);
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      assertTrue(db.queryForObject("SELECT now()<? AND now()<?", Boolean.class, teamDeadline, businessDeadline));
      until(() -> Boolean.TRUE.equals(db.queryForObject("SELECT clock_timestamp()>? AND clock_timestamp()>?", Boolean.class, teamDeadline, businessDeadline)), "Both invitations must expire after transaction start");
      var teamInvitations = (java.util.List<?>) teams.mine(request("expiry-buyer")).get("invitations");
      var businessInvitations = (java.util.List<?>) businesses.mine(request()).get("invitations");
      assertEquals("EXPIRED", ((Map<?, ?>) teamInvitations.get(0)).get("status"));
      assertEquals("EXPIRED", ((Map<?, ?>) businessInvitations.get(0)).get("status"));
      assertEquals("PENDING", db.queryForObject("SELECT status FROM team_invitations", String.class));
      assertEquals("PENDING", db.queryForObject("SELECT status FROM business_invitations", String.class));
    });
  }

  private <T> T afterLockWait(WaitTarget wait, UUID parent, boolean expire, Supplier<T> action)
      throws Exception {
    return afterLockWait(wait, parent, expire, () -> {}, action);
  }

  private <T> T afterLockWait(WaitTarget wait, UUID parent, boolean expire, Runnable beforeRelease,
      Supplier<T> action) throws Exception {
    Timestamp expiry = db.queryForObject("UPDATE team_members SET expires_at=clock_timestamp()+ (? * interval '1 second') WHERE team_id=? AND user_id=? RETURNING expires_at", Timestamp.class, expire ? 2 : 86400, team, member);
    var pool = Executors.newSingleThreadExecutor();
    var pid = new AtomicInteger();
    var begun = new CountDownLatch(1);
    var pending = new AtomicReference<Future<T>>();
    try {
      new TransactionTemplate(transactions).executeWithoutResult(blocker -> {
        if (wait == WaitTarget.MEMBERSHIP)
          db.queryForList("SELECT user_id FROM team_members WHERE team_id=? AND user_id=? FOR UPDATE", team, member);
        else if (parent.equals(engagement))
          db.queryForList("SELECT id FROM delivery_engagements WHERE id=? FOR UPDATE", engagement);
        else if (parent.equals(business))
          db.queryForList("SELECT id FROM businesses WHERE id=? FOR UPDATE", business);
        else
          db.queryForList("SELECT id FROM teams WHERE id=? FOR UPDATE", team);
        pending.set(pool.submit(() -> new TransactionTemplate(transactions).execute(worker -> {
          db.execute("SET LOCAL lock_timeout='6s'");
          pid.set(db.queryForObject("SELECT pg_backend_pid()", Integer.class));
          Timestamp started = db.queryForObject("SELECT now()", Timestamp.class);
          assertTrue(started.before(expiry), "Action must begin before membership expiry");
          begun.countDown();
          T result = action.get();
          if (result instanceof ApiError) worker.setRollbackOnly();
          return result;
        })));
        try {
          assertTrue(begun.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
        until(() -> Boolean.TRUE.equals(db.queryForObject(
            "SELECT cardinality(pg_blocking_pids(?))>0", Boolean.class, pid.get())),
            "Action must actually wait on the selected database row");
        if (expire)
          until(() -> Boolean.TRUE.equals(db.queryForObject(
              "SELECT clock_timestamp()>?", Boolean.class, expiry)),
              "Membership must expire before the blocker releases its lock");
        beforeRelease.run();
      });
      return pending.get().get(8, TimeUnit.SECONDS);
    } finally {
      if (pending.get() != null) pending.get().cancel(true);
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(8, TimeUnit.SECONDS));
    }
  }

  private static void until(BooleanSupplier condition, String message) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) return;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
    fail(message);
  }
}
