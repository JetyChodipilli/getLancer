package com.getlancer.products;

import static com.getlancer.shared.Support.email;
import static com.getlancer.shared.Support.id;

import com.getlancer.responses.MutationResponse;
import com.getlancer.responses.PageResponse;
import com.getlancer.responses.PrivateProjectResponses;
import com.getlancer.responses.ProjectResponses;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Pages;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateProjectService {
  final JdbcTemplate db;
  final Security security;
  final ProductService products;

  public PrivateProjectService(JdbcTemplate db, Security security, ProductService products) {
    this.db = db;
    this.security = security;
    this.products = products;
  }

  public static boolean granted(JdbcTemplate db, UUID product, Map<String, Object> user) {
    return user.get("email_verified_at") != null
        && db.queryForObject(
                "SELECT count(*) FROM product_access_grants WHERE product_id=? AND client_email=?"
                    + " AND expires_at>now()",
                Integer.class,
                product,
                user.get("email"))
            > 0;
  }

  void owner(UUID product, HttpServletRequest r) {
    UUID user = security.developer(r, true);
    if (db.queryForList(
            "SELECT id FROM products WHERE id=? AND owner_user_id=? AND visibility<>'PUBLIC' AND"
                + " approval_status<>'SUSPENDED' FOR UPDATE",
            product,
            user)
        .isEmpty()) throw new ApiError(404, "NOT_FOUND", "Private project not found.");
  }

  @Transactional
  public PageResponse<PrivateProjectResponses.AccessGrant> list(UUID id, HttpServletRequest r) {
    owner(id, r);
    return PageResponse.from(Pages.query(
        db,
        r,
        "SELECT client_email,expires_at FROM product_access_grants WHERE product_id=? ORDER BY"
            + " client_email",
        id), PrivateProjectResponses.AccessGrant::from);
  }

  @Transactional
  public PrivateProjectResponses.Granted grant(UUID id, Map<String, Object> b, HttpServletRequest r) {
    owner(id, r);
    String email = email(b, "email");
    db.update(
        "INSERT INTO product_access_grants(product_id,client_email,expires_at)"
            + " VALUES(?,?,now()+interval '7 days') ON CONFLICT(product_id,client_email) DO UPDATE"
            + " SET expires_at=excluded.expires_at",
        id,
        email);
    return new PrivateProjectResponses.Granted("/private/projects/" + id, 7);
  }

  @Transactional
  public MutationResponse revoke(UUID id, Map<String, Object> b, HttpServletRequest r) {
    owner(id, r);
    db.update(
        "DELETE FROM product_access_grants WHERE product_id=? AND client_email=?",
        id,
        email(b, "email"));
    return MutationResponse.success();
  }

  public ProjectResponses.Project preview(UUID id, HttpServletRequest r) {
    var user = security.principal(r);
    var rows =
        db.queryForList(
            ProductRepository.SELECT
                + " WHERE p.id=? AND p.visibility<>'PUBLIC' AND p.approval_status='APPROVED' AND"
                + " p.lifecycle_status='ACTIVE' AND u.account_status='ACTIVE' AND"
                + " d.approval_status='APPROVED'",
            id);
    if (rows.isEmpty()
        || (!user.get("id").equals(rows.get(0).get("owner_user_id")) && !granted(db, id, user)))
      throw new ApiError(404, "NOT_FOUND", "Private preview unavailable or access expired.");
    return ProjectResponses.Project.from(products.dto(rows.get(0)));
  }
}
