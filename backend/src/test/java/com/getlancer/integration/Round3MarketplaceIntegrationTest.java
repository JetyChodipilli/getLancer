package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.commerce.CommerceStorage;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real HTTP/PG authorization around deterministic private archive I/O; no payment/provider calls. */
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={
    "app.environment=local","app.jobs-enabled=false","app.admin-email=round3-marketplace@example.test",
    "app.admin-password=","app.admin-totp=","spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false",
    "app.storage.access-key=","app.storage.secret-key="
})
@AutoConfigureMockMvc
class Round3MarketplaceIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @MockBean CommerceStorage storage;
  UUID owner,admin,outsider,product,template,version;
  String storageKey,hash;
  byte[] archive;
  Runnable duringStorage=()->{};
  final AtomicInteger reads=new AtomicInteger();

  UUID actor(String name,String role,boolean mfa){
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,name+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",id,role);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified,issued_at) VALUES(?,?,now()+interval '1 hour',?,now())",Support.hash(name),id,mfa);
    return id;
  }
  String path(boolean operator){return "/api/v1/"+(operator?"admin":"me")+"/templates/"+template+"/versions/"+version+"/package";}
  void download(String actor,boolean operator,int expected)throws Exception{
    var response=mvc.perform(get(path(operator)).cookie(new Cookie("gl_session",actor))).andExpect(status().is(expected)).andReturn().getResponse();
    if(expected==200){assertArrayEquals(archive,response.getContentAsByteArray());assertEquals("application/zip",response.getContentType());assertTrue(response.getHeader("Cache-Control").contains("no-store"));}
    else assertFalse(java.util.Arrays.equals(archive,response.getContentAsByteArray()),"Denied request cannot return private package bytes");
  }
  @BeforeEach void fixtures()throws Exception{
    reset(storage);db.execute("TRUNCATE users CASCADE");reads.set(0);duringStorage=()->{};
    owner=actor("round3-owner","DEVELOPER",false);admin=actor("round3-admin","ADMIN",true);outsider=actor("round3-outsider","CLIENT",false);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,'round3-package-owner','Original builder','APPROVED')",owner);
    product=UUID.randomUUID();template=UUID.randomUUID();version=UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,'round3-private-source','Private source fixture','Original package evidence','Source kept private until explicit authorized inspection.','SAAS','CRM','Java','Original source and package notices.','APPROVED','ACTIVE','PUBLIC')",product,owner);
    db.update("INSERT INTO source_templates(id,seller_id,product_id,slug,title,summary,description,price_minor,license_terms) VALUES(?,?,?,'round3-source','Original source','Private template fixture','Original source for current owner or administrator only.',10000,'Preserve all original license and copyright notices.')",template,owner,product);
    var bytes=new ByteArrayOutputStream();
    try(var zip=new ZipOutputStream(bytes)){for(var entry:Map.of("README.md","Original source with complete setup instructions.","LICENSE","Preserve original copyright and permission notices.","package.json","{\"name\":\"authority-fixture\",\"scripts\":{\"build\":\"vite build\"}}").entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
    archive=bytes.toByteArray();hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));storageKey="commerce/"+version+"/"+UUID.randomUUID()+".zip";
    db.update("INSERT INTO source_versions(id,template_id,version,release_notes,storage_key,sha256,size_bytes,entry_count,license_terms,manifest_files,rights_consented_at) VALUES(?,?,'1.0.0','Private original source inspection.',?,?,?,3,'Preserve all original license and copyright notices.','package.json',now())",version,template,storageKey,hash,archive.length);
    when(storage.read(anyString(),anyString(),anyInt())).thenAnswer(call->{
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Do not hold database locks across storage I/O");
      assertEquals(storageKey,call.getArgument(0));assertEquals(hash,call.getArgument(1));assertEquals(archive.length,(Integer)call.getArgument(2));
      reads.incrementAndGet();duringStorage.run();return archive;
    });
  }
  @Test void currentOwnerAndAdministratorReceiveTheExactPrivatePackage()throws Exception{
    download("round3-owner",false,200);download("round3-admin",true,200);assertEquals(2,reads.get());
    assertEquals("DRAFT",db.queryForObject("SELECT status FROM source_versions WHERE id=?",String.class,version));
  }
  @Test void ownerSessionExpiryDuringStorageRejectsBytes()throws Exception{
    duringStorage=()->db.update("UPDATE sessions SET expires_at=clock_timestamp()-interval '1 second' WHERE user_id=?",owner);
    download("round3-owner",false,401);assertEquals(1,reads.get());assertEquals(hash,db.queryForObject("SELECT sha256 FROM source_versions WHERE id=?",String.class,version));
  }
  @Test void ownerAccountSuspensionDuringStorageRejectsBytes()throws Exception{
    duringStorage=()->db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",owner);
    download("round3-owner",false,401);assertEquals(1,reads.get());
  }
  @Test void administratorSessionRemovalDuringStorageRejectsBytes()throws Exception{
    duringStorage=()->db.update("DELETE FROM sessions WHERE user_id=?",admin);
    download("round3-admin",true,401);assertEquals(1,reads.get());
  }
  @Test void administratorRoleRemovalDuringStorageRejectsBytes()throws Exception{
    duringStorage=()->db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'",admin);
    download("round3-admin",true,403);assertEquals(1,reads.get());
  }
  @Test void administratorMfaRevocationDuringStorageRejectsBytes()throws Exception{
    duringStorage=()->db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
    download("round3-admin",true,403);assertEquals(1,reads.get());
  }
  @Test void administratorRecentMfaExpiryDuringStorageRejectsBytes()throws Exception{
    duringStorage=()->db.update("UPDATE sessions SET issued_at=clock_timestamp()-interval '16 minutes' WHERE user_id=?",admin);
    download("round3-admin",true,403);assertEquals(1,reads.get());
  }
  @Test void unrelatedAccountCannotStartPrivateStorageRead()throws Exception{
    download("round3-outsider",false,403);assertEquals(0,reads.get());
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",outsider);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,'round3-unrelated-builder','Unrelated builder','APPROVED')",outsider);
    download("round3-outsider",false,404);assertEquals(0,reads.get());
  }
  @Test void initiallyStaleAdministratorMfaCannotStartStorageRead()throws Exception{
    db.update("UPDATE sessions SET issued_at=clock_timestamp()-interval '16 minutes' WHERE user_id=?",admin);
    download("round3-admin",true,403);assertEquals(0,reads.get());
  }
}
