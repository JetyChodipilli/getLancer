package com.getlancer.profiles;

import com.getlancer.media.MediaService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {
  private static final String ACTIVE_AVATAR = """
      SELECT a.image FROM profile_avatars a JOIN users u ON u.id=a.user_id
      WHERE a.id=? AND u.account_status='ACTIVE'
      """;
  private final JdbcTemplate db;
  private final Security security;

  public AvatarService(JdbcTemplate db, Security security) {
    this.db = db;
    this.security = security;
  }

  public record Picture(String avatarUrl) {}

  public static String url(UUID avatarId) {
    return avatarId == null ? null : "/api/v1/profile-avatars/" + avatarId;
  }

  @Transactional
  public Picture upload(MultipartFile file, HttpServletRequest request) throws IOException {
    UUID user = security.user(request);
    byte[] image = MediaService.avatarUpload(file);
    lockAccount(user, request);
    UUID avatar = UUID.randomUUID();
    db.update("""
        INSERT INTO profile_avatars(user_id,id,image) VALUES(?,?,?)
        ON CONFLICT(user_id) DO UPDATE SET id=excluded.id,image=excluded.image
        """, user, avatar, image);
    security.user(request);
    return new Picture(url(avatar));
  }

  @Transactional
  public void remove(HttpServletRequest request) {
    UUID user = security.user(request);
    lockAccount(user, request);
    db.update("DELETE FROM profile_avatars WHERE user_id=?", user);
    security.user(request);
  }

  private void lockAccount(UUID user, HttpServletRequest request) {
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, user);
    if (!user.equals(security.user(request)))
      throw new ApiError(401, "UNAUTHENTICATED", "Reload your account before continuing.");
  }

  public ResponseEntity<byte[]> read(UUID avatar, HttpServletRequest request) {
    var pictures = db.queryForList(ACTIVE_AVATAR + """
        AND u.email_verified_at IS NOT NULL
        AND EXISTS (SELECT 1 FROM developer_profiles d WHERE d.user_id=a.user_id AND d.approval_status='APPROVED')
        AND EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id=a.user_id AND r.role='DEVELOPER')
        """, byte[].class, avatar);
    if (pictures.isEmpty()) {
      UUID viewer = security.optionalUser(request);
      if (viewer != null)
        pictures = db.queryForList(ACTIVE_AVATAR + " AND a.user_id=?", byte[].class, avatar, viewer);
    }
    if (pictures.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Profile picture unavailable.");
    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "private, no-store").body(pictures.get(0));
  }
}
