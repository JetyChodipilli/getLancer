package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.payments.RazorpayFixture;
import com.getlancer.payments.PaymentRepository;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
  "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
@Import(RazorpayFixture.Configuration.class)
class Round3FinanceIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired RazorpayFixture fixture;
  @Autowired PaymentRepository payments;
  @Autowired PlatformTransactionManager transactions;
  UUID buyer,seller,milestone;
  String key;

  UUID user(String name,boolean builder) {
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,name+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",id,builder?"DEVELOPER":"CLIENT");
    if(builder)db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,name,name);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),id);
    return id;
  }
  MockHttpServletRequestBuilder order(String requestKey) {
    return post("/api/v1/milestones/"+milestone+"/payment-order").cookie(new Cookie("gl_session","round3-finance-buyer"))
      .header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").header("Idempotency-Key",requestKey).contentType("application/json").content("{}");
  }
  @BeforeEach void prepare() {
    fixture.reset();db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.execute("TRUNCATE payment_webhook_events");
    buyer=user("round3-finance-buyer",false);seller=user("round3-finance-seller",true);
    UUID product=UUID.randomUUID(),inquiry=UUID.randomUUID(),engagement=UUID.randomUUID();milestone=UUID.randomUUID();key=UUID.randomUUID().toString();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Portal','Customer portal','Working portal','SAAS','CRM','React, Java','Built it','APPROVED','ACTIVE','PUBLIC')",product,seller,product.toString());
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash) VALUES(?,?,?,'round3-finance-buyer@example.test','Buyer','CUSTOMIZE','Portal delivery','10k','month','DISCUSSION',now(),?,'test')",inquiry,product,seller,UUID.randomUUID());
    db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,buyer_user_id,builder_user_id,title,created_by,status) VALUES(?,?,?,?,'Portal delivery',?,'ACTIVE')",engagement,inquiry,buyer,seller,buyer);
    db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date,status,accepted_by,accepted_at) VALUES(?,?,1,'Portal','Accepted deliverable',10000,current_date+1,'ACCEPTED',?,now())",milestone,engagement,buyer);
    db.update("INSERT INTO payment_accounts(id,builder_user_id,account_id,mode,provider_status,activation_confirmed,verified_by) VALUES(?,?,?,'test','created',true,?)",UUID.randomUUID(),seller,RazorpayFixture.ACCOUNT,buyer);
  }
  void assertOriginalOrder() {
    assertEquals(1,fixture.creates.get());assertTrue(fixture.reservationCommitted);
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_attempts",Integer.class));
    var row=db.queryForMap("SELECT status,account_id,order_id FROM payment_attempts");
    assertEquals("ORDER_CREATED",row.get("status"));assertEquals(RazorpayFixture.ACCOUNT,row.get("account_id"));assertEquals(fixture.orderId(),row.get("order_id"));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM payment_ledger",Integer.class));
  }
  void revoke(String change) {
    switch(change) {
      case "suspended" -> db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",seller);
      case "unapproved" -> db.update("UPDATE developer_profiles SET approval_status='DRAFT' WHERE user_id=?",seller);
      case "role-removed" -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'",seller);
      case "remapped" -> db.update("UPDATE payment_accounts SET account_id='acc_replacement123456' WHERE builder_user_id=?",seller);
      default -> throw new AssertionError("Unknown fixture change");
    }
  }
  @Test void currentSellerMayCreateAndReuseOriginalCheckout() throws Exception {
    mvc.perform(order(key)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDER_CREATED"));
    mvc.perform(order(key)).andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(fixture.orderId()));
    mvc.perform(order(UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(fixture.orderId()));assertOriginalOrder();
  }
  @ParameterizedTest @ValueSource(strings={"suspended","unapproved","role-removed","remapped"})
  void reusedCheckoutRequiresCurrentSellerAndFrozenRecipient(String change) throws Exception {
    mvc.perform(order(key)).andExpect(status().isOk());revoke(change);
    for(String requestKey:new String[]{key,UUID.randomUUID().toString()})mvc.perform(order(requestKey)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(change.equals("remapped")?"PAYEE_CHANGED":"PAYEE_UNAVAILABLE"));
    assertOriginalOrder();
    db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",seller);db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",seller);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER') ON CONFLICT DO NOTHING",seller);db.update("UPDATE payment_accounts SET account_id=? WHERE builder_user_id=?",RazorpayFixture.ACCOUNT,seller);
    mvc.perform(order(key)).andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(fixture.orderId()));assertOriginalOrder();
  }
  @ParameterizedTest @ValueSource(strings={"suspended","remapped"})
  void providerCreateFactsSurviveSellerChangeWhileCheckoutIsDenied(String change) throws Exception {
    fixture.blocked=true;var executor=Executors.newSingleThreadExecutor();
    try {
      var pending=executor.submit(()->mvc.perform(order(key)).andReturn().getResponse());
      assertTrue(fixture.createEntered.await(1,TimeUnit.SECONDS),"Actual provider POST must start before seller changes");revoke(change);fixture.releaseCreate.countDown();
      var response=pending.get(5,TimeUnit.SECONDS);assertEquals(409,response.getStatus(),response.getContentAsString());assertTrue(response.getContentAsString().contains(change.equals("remapped")?"PAYEE_CHANGED":"PAYEE_UNAVAILABLE"));
      assertOriginalOrder();mvc.perform(order(key)).andExpect(status().isConflict());assertEquals(1,fixture.creates.get());
    } finally {fixture.releaseCreate.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }

  @ParameterizedTest @ValueSource(strings={"builder/unapproved","builder/role-removed","team/unapproved","team/role-removed","team/member-removed"})
  void payeeLockWaitResamplesJoinedSellerAuthority(String scenario) throws Exception {
    String[] parts=scenario.split("/");
    Map<String,Object> party=new HashMap<>();
    UUID team=UUID.randomUUID();
    if(parts[0].equals("team")) {
      db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id) VALUES(?,?,'Finance studio','Delivery studio','AVAILABLE_NOW','10k',?)",team,team.toString(),seller);
      db.update("INSERT INTO team_members(team_id,user_id,role,membership_type) VALUES(?,?,'OWNER','PERMANENT')",team,seller);
      db.update("UPDATE payment_accounts SET builder_user_id=NULL,team_id=? WHERE builder_user_id=?",team,seller);
      party.put("teamId",team);
    } else party.put("builderUserId",seller);
    var tx=new TransactionTemplate(transactions);tx.setTimeout(15);
    assertEquals(RazorpayFixture.ACCOUNT,tx.execute(status->payments.payee(party,"test")),"Current seller positive control must use the real repository");
    var executor=Executors.newSingleThreadExecutor();
    var started=new CountDownLatch(1);var waiter=new AtomicInteger();
    var pending=new AtomicReference<Future<String>>();
    try {
      tx.executeWithoutResult(held->{
        db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,seller);
        int holder=db.queryForObject("SELECT pg_backend_pid()",Integer.class);
        pending.set(executor.submit(()->tx.execute(status->{
          waiter.set(db.queryForObject("SELECT pg_backend_pid()",Integer.class));started.countDown();
          try {return payments.payee(party,"test");}
          catch(ApiError error) {return error.code;}
        })));
        try {
          assertTrue(started.await(5,TimeUnit.SECONDS));
          long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();boolean blocked=false;
          while(System.nanoTime()<deadline) {
            blocked=Boolean.TRUE.equals(db.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,holder,waiter.get()));
            if(blocked)break;
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
          }
          assertTrue(blocked,"The actual payee query must wait on the held seller lock before authority changes");
          if(parts[1].equals("member-removed"))db.update("DELETE FROM team_members WHERE team_id=? AND user_id=?",team,seller);
          else revoke(parts[1]);
        } catch(InterruptedException error) {Thread.currentThread().interrupt();throw new RuntimeException(error);}
      });
      assertEquals("PAYEE_UNAVAILABLE",pending.get().get(10,TimeUnit.SECONDS));
      assertEquals(0,fixture.creates.get());
      assertEquals(0,db.queryForObject("SELECT count(*) FROM payment_attempts",Integer.class));
    } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
}
