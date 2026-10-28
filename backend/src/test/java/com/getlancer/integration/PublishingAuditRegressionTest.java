package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={
 "app.environment=local","app.jobs-enabled=false","app.admin-email=","app.admin-password=","app.admin-totp=",
 "app.mfa.active-key-id=v1","app.mfa.keyring=v1:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
 "spring.config.import=","spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
 "spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test",
 "app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class PublishingAuditRegressionTest {
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 UUID owner,outsider,product,first,second;
 final String session="publishing-audit-owner",otherSession="publishing-audit-outsider";

 @BeforeEach void prepare(){
  db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");
  owner=user("publishing-owner@example.test",session);outsider=user("publishing-outsider@example.test",otherSession);product=UUID.randomUUID();first=UUID.randomUUID();second=UUID.randomUUID();
  db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Publishing proof','A proof editing fixture','Complete original proof editing fixture','SAAS','Inventory','React','Implemented the original interaction','DRAFT','DRAFT','PUBLIC')",product,owner,product.toString());
  media(first,0,"First proof image");media(second,1,"Second proof image");
 }
 @AfterEach void clear(){db.execute("TRUNCATE users CASCADE");}
 UUID user(String email,String token){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,email);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,id.toString(),email);db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",id);db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);return id;}
 void media(UUID id,int order,String alt){db.update("INSERT INTO product_media(id,product_id,storage_key,content_type,size_bytes,verified,alt_text,sort_order) VALUES(?,?,?,'image/png',10,true,?,?)",id,product,"publishing-audit/"+id,alt,order);}
 MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String token){return request.cookie(new Cookie("gl_session",token)).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer");}
 String path(){return "/api/v1/developer/products/"+product;}
 String order(boolean both){return "{\"items\":[{\"id\":\""+second+"\",\"alt\":\"Updated cover description\"}"+(both?",{\"id\":\""+first+"\",\"alt\":\"Updated second description\"}":"")+"]}";}

 @Test void reorderedProofReadsItsCurrentCoverAndDescriptions() throws Exception {
  mvc.perform(as(patch(path()+"/media"),session).contentType("application/json").content(order(true))).andExpect(status().isOk());
  mvc.perform(as(get(path()+"/proof"),session)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second.toString())).andExpect(jsonPath("$.items[0].alt").value("Updated cover description")).andExpect(jsonPath("$.items[1].id").value(first.toString()));
 }
 @Test void incompleteProofResyncRollsBackEarlierDescriptionAndOrderWrites() throws Exception {
  mvc.perform(as(patch(path()+"/media"),session).contentType("application/json").content(order(false))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MEDIA_CHANGED"));
  assertEquals("Second proof image",db.queryForObject("SELECT alt_text FROM product_media WHERE id=?",String.class,second));assertEquals(1,db.queryForObject("SELECT sort_order FROM product_media WHERE id=?",Integer.class,second));
 }
 @Test void anotherBuilderCannotReadReorderOrDeletePrivateProof() throws Exception {
  mvc.perform(as(get(path()+"/proof"),otherSession)).andExpect(status().isNotFound());mvc.perform(as(patch(path()+"/media"),otherSession).contentType("application/json").content(order(true))).andExpect(status().isNotFound());mvc.perform(as(delete(path()+"/media/"+first),otherSession)).andExpect(status().isNotFound());assertEquals(2,db.queryForObject("SELECT count(*) FROM product_media WHERE product_id=?",Integer.class,product));
 }
 @Test void submittedProofCannotBeMutatedAndCurrentAuthorityIsRechecked() throws Exception {
  db.update("UPDATE products SET approval_status='PENDING_REVIEW' WHERE id=?",product);mvc.perform(as(delete(path()+"/media/"+first),session)).andExpect(status().isNotFound());mvc.perform(as(patch(path()+"/media"),session).contentType("application/json").content(order(true))).andExpect(status().isNotFound());
  db.update("UPDATE products SET approval_status='DRAFT' WHERE id=?",product);db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'",owner);mvc.perform(as(delete(path()+"/media/"+first),session)).andExpect(status().isForbidden());assertEquals(2,db.queryForObject("SELECT count(*) FROM product_media WHERE product_id=?",Integer.class,product));
 }
}
