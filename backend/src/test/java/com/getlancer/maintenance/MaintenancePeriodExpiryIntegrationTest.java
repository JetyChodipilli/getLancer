package com.getlancer.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.getlancer.security.Security;
import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.testing.TestDatabaseGuard;
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
class MaintenancePeriodExpiryIntegrationTest {
  enum WaitTarget { SUBSCRIPTION, PERIOD, ELIGIBILITY }

  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MaintenanceRepository repository;
  UUID buyer, seller, engagement, offer, subscription, period;

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE maintenance_webhook_events");
    buyer = user("period-buyer");
    seller = user("period-seller");
    UUID business = UUID.randomUUID(), brief = UUID.randomUUID();
    engagement = UUID.randomUUID();
    offer = UUID.randomUUID();
    subscription = UUID.randomUUID();
    period = UUID.randomUUID();
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Period business','Private support fixture',?)", business, buyer);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", business, buyer);
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status) VALUES(?,?,?,'Period brief','A private support project fixture','CRM','Java','10000','One month','OPEN')", brief, business, buyer);
    db.update("INSERT INTO delivery_engagements(id,business_request_id,business_id,builder_user_id,title,created_by,status) VALUES(?,?,?,?,'Completed support fixture',?,'COMPLETED')", engagement, brief, business, seller, buyer);
    db.update("INSERT INTO maintenance_offers(id,engagement_id,revision,status,title,scope,terms,amount_minor,requests_per_cycle,response_hours,total_cycles,seller_id,seller_consented_at,buyer_id,buyer_consented_at,digest) VALUES(?,?,1,'ACCEPTED','Period care','Maintain the delivered customer portal','Small fixes only; new features require another agreement.',10000,2,24,12,?,now(),?,now(),?)", offer, engagement, seller, buyer, "a".repeat(64));
    // Disposable synthetic financial records isolate period timing; no merchant charge is asserted.
    db.update("INSERT INTO maintenance_subscriptions(id,engagement_id,offer_id,payer_id,request_key,amount_minor,currency,requests_per_cycle,response_hours,total_cycles,digest,account_id,mode,status,creation_step,plan_state,subscription_state,provider_plan_id,provider_subscription_id,reconciled_at) VALUES(?,?,?,?,?,10000,'INR',2,24,12,?,'acc_period_fixture','live','ACTIVE','COMPLETE','CONFIRMED','CONFIRMED','plan_period_fixture','sub_period_fixture',clock_timestamp())", subscription, engagement, offer, buyer, UUID.randomUUID(), "a".repeat(64));
  }

  private UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    return id;
  }

  private Timestamp insertPeriod(int secondsUntilEnd) {
    return db.queryForObject("INSERT INTO maintenance_periods(id,subscription_id,provider_invoice_id,payment_id,order_id,amount_minor,currency,period_start,period_end,transfer_key,transfer_state,transfer_status,reconciled_at) VALUES(?,?,'inv_period_fixture','pay_period_fixture','order_period_fixture',10000,'INR',clock_timestamp()-interval '29 days',clock_timestamp()+(? * interval '1 second'),?,'CONFIRMED','PROCESSED',clock_timestamp()) RETURNING period_end", Timestamp.class, period, subscription, secondsUntilEnd, UUID.randomUUID());
  }

  @ParameterizedTest
  @EnumSource(value = WaitTarget.class, names = {"SUBSCRIPTION", "PERIOD"})
  void currentPeriodExpiresDuringParentOrPeriodRowWait(WaitTarget wait) throws Exception {
    assertNull(afterLockWait(wait, true));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM maintenance_requests", Integer.class));
  }

  @ParameterizedTest
  @EnumSource(value = WaitTarget.class, names = {"SUBSCRIPTION", "PERIOD"})
  void unexpiredPeriodRemainsAvailableAfterParentOrPeriodRowWait(WaitTarget wait) throws Exception {
    var active = afterLockWait(wait, false);
    assertNotNull(active);
    assertEquals(period, active.get("id"));
    assertEquals(0L, ((Number) active.get("used_requests")).longValue());
  }

  @Test
  void existingRequestCanContinueAfterItsPaidPeriodExpires() {
    insertPeriod(-86400);
    UUID requestId = UUID.randomUUID();
    db.update("INSERT INTO maintenance_requests(id,subscription_id,period_id,actor_id,request_key,title,description,response_due_at,created_at) VALUES(?,?,?,?,?,'Prior request','Retained work opened during the paid period.',now()-interval '1 day',now()-interval '2 days')", requestId, subscription, period, buyer, UUID.randomUUID());
    assertNull(new TransactionTemplate(transactions).execute(tx -> repository.currentPeriod(subscription)));
    var access = mock(MaintenanceAccess.class);
    var billing = mock(MaintenanceBillingService.class);
    var security = mock(Security.class);
    var request = new MockHttpServletRequest();
    // Current party permission is isolated here; membership revocation has separate real-DB tests.
    when(access.offer(offer, request, "SELLER", true)).thenReturn(Map.of("id", offer));
    when(security.user(request)).thenReturn(seller);
    var service = new MaintenanceService(repository, access, billing, security, transactions);
    assertEquals("IN_PROGRESS", service.requestAction(offer, requestId, "start", Map.of(), request).get("status"));
    assertEquals("IN_PROGRESS", db.queryForObject("SELECT status FROM maintenance_requests WHERE id=?", String.class, requestId));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM maintenance_requests WHERE period_id=?", Integer.class, period));
  }

  @Test
  void entitlementChecksPeriodAfterCurrentPartyLockWait() throws Exception {
    var delivery = mock(DeliveryRepository.class);
    var security = mock(Security.class);
    var access = spy(new MaintenanceAccess(repository, delivery, security));
    var request = new MockHttpServletRequest();
    var currentEngagement = Map.<String, Object>of("id", engagement);
    doReturn(repository.offer(offer, false)).when(access).offer(offer, request, "ANY", false);
    when(delivery.lockEngagement(engagement, request, "ANY")).thenReturn(currentEngagement);
    doAnswer(call -> {
      db.queryForList("SELECT id FROM users WHERE id=? FOR SHARE", buyer);
      return true;
    }).when(access).eligible(currentEngagement);
    var service = new MaintenanceService(repository, access, mock(MaintenanceBillingService.class), security, transactions);
    var detail = afterLockWait(WaitTarget.ELIGIBILITY, true, () -> service.detail(offer, request));
    assertEquals(false, ((Map<?, ?>) detail.get("entitlement")).get("available"));
  }

  private Map<String, Object> afterLockWait(WaitTarget wait, boolean expire) throws Exception {
    return afterLockWait(wait, expire, () -> {
      repository.billing(subscription, true);
      return repository.currentPeriod(subscription);
    });
  }

  private <T> T afterLockWait(WaitTarget wait, boolean expire, Supplier<T> action) throws Exception {
    Timestamp deadline = insertPeriod(expire ? 2 : 86400);
    var pool = Executors.newSingleThreadExecutor();
    var begun = new CountDownLatch(1);
    var pid = new AtomicInteger();
    var pending = new AtomicReference<Future<T>>();
    try {
      new TransactionTemplate(transactions).executeWithoutResult(blocker -> {
        if (wait == WaitTarget.SUBSCRIPTION)
          db.queryForList("SELECT id FROM maintenance_subscriptions WHERE id=? FOR UPDATE", subscription);
        else if (wait == WaitTarget.PERIOD)
          db.queryForList("SELECT id FROM maintenance_periods WHERE id=? FOR UPDATE", period);
        else
          db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", buyer);
        pending.set(pool.submit(() -> new TransactionTemplate(transactions).execute(worker -> {
          db.execute("SET LOCAL lock_timeout='6s'");
          pid.set(db.queryForObject("SELECT pg_backend_pid()", Integer.class));
          assertTrue(db.queryForObject("SELECT now()<?", Boolean.class, deadline));
          begun.countDown();
          return action.get();
        })));
        try {
          assertTrue(begun.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
        until(() -> Boolean.TRUE.equals(db.queryForObject(
            "SELECT cardinality(pg_blocking_pids(?))>0", Boolean.class, pid.get())),
            "Current-period lookup must actually wait on the selected row");
        if (expire)
          until(() -> Boolean.TRUE.equals(db.queryForObject(
              "SELECT clock_timestamp()>?", Boolean.class, deadline)),
              "The paid period must end before the blocker releases its lock");
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
