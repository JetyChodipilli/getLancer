package com.getlancer;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.getlancer.Support.*;
@RestController @RequestMapping("/api/v1") class PrivateProjects {
 final JdbcTemplate db;final Security security;final ProductsController products;
 PrivateProjects(JdbcTemplate db,Security security,ProductsController products){this.db=db;this.security=security;this.products=products;}
 static boolean granted(JdbcTemplate db,UUID product,Map<String,Object> user){return user.get("email_verified_at")!=null&&db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=? AND client_email=? AND expires_at>now()",Integer.class,product,user.get("email"))>0;}
 void owner(UUID product,HttpServletRequest r){UUID user=security.developer(r,true);if(db.queryForList("SELECT id FROM products WHERE id=? AND owner_user_id=? AND visibility<>'PUBLIC' AND approval_status<>'SUSPENDED' FOR UPDATE",product,user).isEmpty())throw new ApiError(404,"NOT_FOUND","Private project not found.");}
 @GetMapping("/developer/products/{id}/access") @Transactional Map<String,Object> list(@PathVariable UUID id,HttpServletRequest r){owner(id,r);return Pages.query(db,r,"SELECT client_email,expires_at FROM product_access_grants WHERE product_id=? ORDER BY client_email",id);}
 @PostMapping("/developer/products/{id}/access") @Transactional Map<String,Object> grant(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){owner(id,r);String email=email(b,"email");db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '7 days') ON CONFLICT(product_id,client_email) DO UPDATE SET expires_at=excluded.expires_at",id,email);return Map.of("path","/private/projects/"+id,"expiresInDays",7);}
 @DeleteMapping("/developer/products/{id}/access") @Transactional Map<String,Object> revoke(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){owner(id,r);db.update("DELETE FROM product_access_grants WHERE product_id=? AND client_email=?",id,email(b,"email"));return Map.of("ok",true);}
 @GetMapping("/private/products/{id}") Map<String,Object> preview(@PathVariable UUID id,HttpServletRequest r){var user=security.principal(r);var rows=db.queryForList(ProductsController.SELECT+" WHERE p.id=? AND p.visibility<>'PUBLIC' AND p.approval_status='APPROVED' AND p.lifecycle_status='ACTIVE' AND u.account_status='ACTIVE' AND d.approval_status='APPROVED'",id);if(rows.isEmpty()||(!user.get("id").equals(rows.get(0).get("owner_user_id"))&&!granted(db,id,user)))throw new ApiError(404,"NOT_FOUND","Private preview unavailable or access expired.");return products.dto(rows.get(0));}
}
