package com.getlancer;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.getlancer.Support.*;
@Service class GitHubAccounts {
 @org.springframework.beans.factory.annotation.Value("${app.legal-version:v1-draft}") String legalVersion="v1-draft";
 record Identity(String subject,String email,String name){}
 final JdbcTemplate db;final Security security;final String adminEmail;
 GitHubAccounts(JdbcTemplate db,Security security,@Value("${app.admin-email}")String adminEmail){this.db=db;this.security=security;this.adminEmail=adminEmail.trim().toLowerCase(Locale.ROOT);}
 @Transactional UUID resolve(Identity identity,String intent){
  String email=identity.email().toLowerCase(Locale.ROOT),subject=identity.subject();
  if(email.equals(adminEmail))throw new ApiError(403,"admin_password","Use administrator password sign-in.");
  db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","github:"+subject);
  var linked=db.queryForList("SELECT u.* FROM users u JOIN oauth_identities i ON i.user_id=u.id WHERE i.provider='github' AND i.subject=? FOR UPDATE OF u",subject);
  if(!linked.isEmpty()){var user=linked.get(0);UUID id=(UUID)user.get("id");if(security.role(id,"ADMIN"))throw new ApiError(403,"admin_password","Use administrator password sign-in.");if(!user.get("account_status").equals("ACTIVE")||!user.get("email").equals(email))throw new ApiError(403,"account_unavailable","Account unavailable.");return id;}
  if(db.queryForObject("SELECT count(*) FROM users WHERE email=?",Integer.class,email)>0)throw new ApiError(409,"email_account","Sign in with your existing account method.");
  if(!intent.equals("signup"))throw new ApiError(409,"signup_required","Create an account first.");
  UUID user=id();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,?,now())",user,email,new BCryptPasswordEncoder(12).encode(randomToken()));
  db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",user,user);
  String name=identity.name();if(name.length()>100)name=name.substring(0,100);
  db.update("INSERT INTO developer_profiles(user_id,slug,display_name) VALUES(?,?,?)",user,"builder-"+user,name);
  db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",user);
  db.update("INSERT INTO legal_acceptances(user_id,document_version) VALUES(?,?)",user,legalVersion);
  db.update("INSERT INTO oauth_identities(provider,subject,user_id) VALUES('github',?,?)",subject,user);return user;
 }
}
