package com.getlancer.products;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
  private final JdbcTemplate db;

  public ProductRepository(JdbcTemplate db) {
    this.db = db;
  }

  public static final String PUBLIC =
      "p.approval_status='APPROVED' AND p.lifecycle_status='ACTIVE' AND p.visibility='PUBLIC' AND"
          + " u.account_status='ACTIVE' AND d.approval_status='APPROVED'";
  public static final String SELECT =
      "SELECT p.*,d.display_name AS builder,d.slug AS builder_slug,d.availability_status AS"
          + " availability,d.booked_until,u.account_status AS owner_status,d.approval_status AS"
          + " profile_status FROM products p JOIN users u ON u.id=p.owner_user_id JOIN"
          + " developer_profiles d ON d.user_id=u.id";

  List<Map<String, Object>> media(Object product) {
    return db.queryForList(
        "SELECT id,alt_text FROM product_media WHERE product_id=? AND verified=true ORDER BY"
            + " sort_order,created_at,id LIMIT 6",
        product);
  }

  List<Map<String, Object>> mediaForProducts(Object[] ids) {
    return db.queryForList(
        "SELECT id,product_id,alt_text FROM product_media WHERE verified=true AND product_id IN"
            + " ("
            + String.join(",", Collections.nCopies(ids.length, "?"))
            + ") ORDER BY sort_order,created_at,id",
        ids);
  }

  List<Map<String, Object>> findOwnedForUpdate(UUID p, UUID u) {
    return db.queryForList(
        "SELECT * FROM products WHERE id=? AND owner_user_id=? FOR UPDATE", p, u);
  }

  List<Map<String, Object>> findPublic(String slug) {
    return db.queryForList(SELECT + " WHERE p.slug=? AND " + PUBLIC, slug);
  }

  Map<String, Object> reviewSummary(UUID builder) {
    return db.queryForMap(
        "SELECT count(*) AS \"reviewCount\",round(avg(r.rating),1) AS \"averageRating\" FROM"
            + " reviews r JOIN inquiries i ON i.id=r.inquiry_id WHERE r.developer_user_id=? AND"
            + " r.moderation_status='PUBLISHED' AND i.moderation_status='CLEAR' AND"
            + " i.current_status='COMPLETED' AND i.email_confirmed_at IS NOT NULL",
        builder);
  }

  int matchingVerificationCount(String slug) {
    return db.queryForObject(
        "SELECT count(*) FROM repository_verifications v JOIN products p ON"
            + " p.id=v.product_id WHERE p.slug=? AND v.status='VERIFIED' AND"
            + " v.repository_url=p.repository_url",
        Integer.class,
        slug);
  }
}
