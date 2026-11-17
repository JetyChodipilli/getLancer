package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PrivilegedReviewClockIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired DataSource source;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @org.springframework.boot.test.mock.mockito.MockBean com.getlancer.payments.RazorpayClient provider;
  static final String TOKEN="queued-review-admin";
  UUID admin,owner,product,component,template,version,purchase;
  String cachedHash;
  enum Kind { COMPONENT_REVIEW, COMPONENT_DETAIL, COLLEGE_REVIEW, SLOT_PRICE, COMMERCE_REVIEW, SLOT_PRICE_AUDIT, COMMERCE_REVIEW_ACTOR, SLOT_BIND_AUDIT }

  @BeforeEach void fixtures() {
    db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");
    admin=UUID.randomUUID();owner=UUID.randomUUID();product=UUID.randomUUID();component=UUID.randomUUID();template=UUID.randomUUID();version=UUID.randomUUID();purchase=UUID.randomUUID();
    for(UUID id:new UUID[]{admin,owner})db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',clock_timestamp())",id,id+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN'),(?,'DEVELOPER')",admin,owner);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,'review-clock-builder','Clock builder','APPROVED')",owner);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified,issued_at) VALUES(?,?,clock_timestamp()+interval '1 hour',true,clock_timestamp())",Support.hash(TOKEN),admin);
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,rights_confirmed) VALUES(?,?,'review-clock-project','Clock project','Synthetic review regression','Controlled review fixture','SAAS','Inventory','Java','Original implementation',true)",product,owner);
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status) VALUES(?,?,'studio-navigation','review-clock-component','Clock component','Synthetic review regression','Original component contribution','PENDING')",component,owner);
    db.update("INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution) VALUES(?,'FULL_STACK','Java','Original problem','Expected outcome','Local Java runtime','Original contribution')",product);
    db.update("INSERT INTO component_slot_purchases(id,owner_id,idempotency_key,amount_minor,mode,status,pool) VALUES(?,?,?,10000,'live','UNKNOWN','PROJECT')",purchase,owner,UUID.randomUUID());
    when(provider.mode()).thenReturn("live");when(provider.keyId()).thenReturn("rzp_live_fixtureonly123");
    when(provider.configuration()).thenReturn(Map.of("enabled",true,"mode","live","reason",""));
    when(provider.order(anyString())).thenReturn(json.valueToTree(Map.of("id","order_clockfixture123","receipt",purchase.toString(),"amount",10000,"currency","INR")));
    when(provider.orderPayments(anyString())).thenReturn(json.valueToTree(Map.of("items",java.util.List.of())));
    db.update("INSERT INTO source_templates(id,seller_id,product_id,slug,title,summary,description,price_minor,license_terms) VALUES(?,?,?,'review-clock-template','Clock template','Synthetic review regression','Controlled review fixture',10000,'Preserve notices')",template,owner,product);
    db.update("INSERT INTO source_versions(id,template_id,version,release_notes,storage_key,sha256,size_bytes,entry_count,license_terms,manifest_files,rights_consented_at,status) VALUES(?,?,'1.0.0','Review clock fixture',?, ?,100,3,'Preserve notices','README.md\nLICENSE\npackage.json',now(),'PENDING')",version,template,"clock/"+version,"a".repeat(64));
  }

  Map<String,String> assessment(){return Map.of("result","PASS","note","Independent review of this exact controlled source.");}
  ResultActions request(Kind kind)throws Exception {
    MockHttpServletRequestBuilder request=switch(kind){
      case SLOT_BIND_AUDIT->post("/api/v1/admin/publishing-slot-purchases/"+purchase+"/bind-order").content("{\"orderId\":\"order_clockfixture123\",\"reason\":\"Independently verified original provider receipt.\"}");
      case COMPONENT_DETAIL->get("/api/v1/admin/components/"+component);
      case COMPONENT_REVIEW->post("/api/v1/admin/components/"+component+"/review").content(json.writeValueAsString(Map.of("revision",1,"sourceHash",cachedHash,"decision","CHANGES_REQUESTED","reason","Review clock regression needs a documented change.","originality",assessment(),"quality",assessment(),"license",assessment(),"execution",assessment())));
      case COLLEGE_REVIEW->post("/api/v1/admin/college-projects/"+product+"/review").content("{\"revision\":1,\"decision\":\"CHANGES_REQUESTED\",\"reason\":\"Review clock regression needs a documented change.\"}");
      case SLOT_PRICE,SLOT_PRICE_AUDIT->put("/api/v1/admin/publishing-slots/PROJECT/pricing").content("{\"amountMinor\":12345,\"enabled\":true}");
      case COMMERCE_REVIEW,COMMERCE_REVIEW_ACTOR->post("/api/v1/admin/templates/"+template+"/versions/"+version+"/review").content("{\"action\":\"CHANGES_REQUESTED\",\"reason\":\"Review clock regression needs a documented change.\",\"rightsReviewed\":true,\"packageReviewed\":true}");
    };
    return mvc.perform(request.cookie(new Cookie("gl_session",TOKEN)).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json"));
  }
  String componentHash()throws Exception {
    return json.readTree(mvc.perform(get("/api/v1/admin/components/"+component).cookie(new Cookie("gl_session",TOKEN))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("sourceHash").asText();
  }
  Map<String,Object> state(){return Map.of("purchase",db.queryForMap("SELECT order_id,status,updated_at FROM component_slot_purchases WHERE id=?",purchase),"component",db.queryForMap("SELECT status,revision,review_reason FROM component_entries WHERE id=?",component),"college",db.queryForMap("SELECT status,revision,review_reason FROM college_project_metadata WHERE product_id=?",product),"price",db.queryForMap("SELECT amount_minor,enabled,updated_at FROM component_slot_pricing WHERE pool='PROJECT'"),"source",db.queryForMap("SELECT status,review_reason,reviewed_at FROM source_versions WHERE id=?",version),"audit",db.queryForObject("SELECT count(*) FROM component_audit",Integer.class),"commerceAudit",db.queryForObject("SELECT count(*) FROM commerce_audit",Integer.class));}

  void queued(Kind kind,boolean expires,boolean logout)throws Exception {
    cachedHash=componentHash();
    var before=state();var executor=Executors.newSingleThreadExecutor();
    try(var holder=source.getConnection()){
      holder.setAutoCommit(false);
      int pid;try(var statement=holder.createStatement();var rows=statement.executeQuery("SELECT pg_backend_pid()")){rows.next();pid=rows.getInt(1);}
      String lock=switch(kind){
        case COMPONENT_DETAIL,SLOT_PRICE_AUDIT,COMMERCE_REVIEW_ACTOR,SLOT_BIND_AUDIT->"SELECT id FROM users WHERE id='"+admin+"' FOR UPDATE";
        case COMPONENT_REVIEW->"SELECT id FROM component_entries WHERE id='"+component+"' FOR UPDATE";
        case COLLEGE_REVIEW->"SELECT product_id FROM college_project_metadata WHERE product_id='"+product+"' FOR UPDATE";
        case SLOT_PRICE->"SELECT pool FROM component_slot_pricing WHERE pool='PROJECT' FOR UPDATE";
        case COMMERCE_REVIEW->"SELECT pg_advisory_xact_lock(hashtextextended('publishing:"+owner+"',0))";
      };
      try(var statement=holder.createStatement()){statement.execute(lock);}
      try{
        db.update("UPDATE sessions SET issued_at=clock_timestamp()-"+(expires?"interval '896 seconds'":"interval '60 seconds'")+" WHERE user_id=?",admin);
        Timestamp deadline=db.queryForObject("SELECT issued_at+interval '15 minutes' FROM sessions WHERE user_id=?",Timestamp.class,admin);
        var response=executor.submit(()->request(kind));
        boolean blocked=false;long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<stop){if(db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND ?=ANY(pg_blocking_pids(pid))",Integer.class,pid)>0){blocked=true;break;}LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));}
        assertTrue(blocked,"Request must reach the intended lock while MFA is recent");
        assertTrue(db.queryForObject("SELECT clock_timestamp()<?",Boolean.class,deadline));
        if(logout)mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("gl_session",TOKEN)).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer")).andExpect(status().isNoContent());
        if(expires)db.queryForObject("SELECT pg_sleep(GREATEST(0,extract(epoch FROM (?::timestamptz-clock_timestamp()))))",Object.class,deadline);
        holder.commit();var result=response.get(10,TimeUnit.SECONDS);
        if(expires||logout){assertEquals(before,state(),"Denied review must roll back all domain writes and audit");result.andExpect(status().is(logout?401:403));}
        else {result.andExpect(status().isOk());if(kind!=Kind.COMPONENT_DETAIL)assertNotEquals(before,state(),"Current authority must still permit the decision");}
      }finally{holder.rollback();}
    }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
  }
  @ParameterizedTest @EnumSource(Kind.class) void expiryAfterWaitCannotReadOrMutate(Kind kind)throws Exception{queued(kind,true,false);}
  @ParameterizedTest @EnumSource(Kind.class) void currentAuthorityAfterWaitStillSucceeds(Kind kind)throws Exception{queued(kind,false,false);}
  @org.junit.jupiter.api.Test void logoutWhileCommerceReviewWaitsRevokesDecision()throws Exception{queued(Kind.COMMERCE_REVIEW,false,true);}
  @org.junit.jupiter.api.Test void logoutWhileBindReconciliationWaitsCannotReturnPrivateReceipt()throws Exception{
    var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
    when(provider.orderPayments(anyString())).thenAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return json.valueToTree(Map.of("items",java.util.List.of()));});
    var executor=Executors.newSingleThreadExecutor();
    try{
      var response=executor.submit(()->request(Kind.SLOT_BIND_AUDIT));assertTrue(entered.await(5,TimeUnit.SECONDS));
      assertEquals("order_clockfixture123",db.queryForObject("SELECT order_id FROM component_slot_purchases WHERE id=?",String.class,purchase));
      mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("gl_session",TOKEN)).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer")).andExpect(status().isNoContent());
      release.countDown();response.get(10,TimeUnit.SECONDS).andExpect(status().isUnauthorized());
      assertEquals(0,db.queryForObject("SELECT count(*) FROM component_slot_ledger",Integer.class));
    }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
  }
}
