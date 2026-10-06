package com.getlancer.notifications;

import static com.getlancer.shared.Support.email;
import static com.getlancer.shared.Support.hash;
import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.randomToken;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Mail {
  final boolean jobsEnabled;

  final JdbcTemplate db;
  final JavaMailSender sender;
  final TransactionTemplate tx;
  final String origin, from;

  public Mail(JdbcTemplate db, JavaMailSender sender, PlatformTransactionManager tm, String origin, String from) {
    this(db, sender, tm, origin, from, false);
  }

  @Autowired
  public Mail(JdbcTemplate db, JavaMailSender sender, PlatformTransactionManager tm,
      @Value("${app.origin}") String origin, @Value("${app.email-from}") String from,
      @Value("${app.jobs-enabled:true}") boolean jobsEnabled) {
    this.db = db;
    this.sender = sender;
    tx = new TransactionTemplate(tm);
    this.origin = origin;
    this.from = from;
    this.jobsEnabled = jobsEnabled;
  }

  public void enqueue(String recipient, String subject, String body) {
    db.update(
        "INSERT INTO email_outbox(id,recipient,subject,body) VALUES(?,?,?,?)",
        id(),
        recipient,
        subject,
        body);
  }

  public void token(String kind, UUID user, UUID inquiry, String email) {
    String token = randomToken();
    boolean closure = kind.equals("ACCOUNT_DELETION");
    db.update(
        "INSERT INTO account_tokens(id,token_hash,kind,user_id,inquiry_id,email,expires_at)"
            + " VALUES(?,?,?,?,?,?,now()+(?*interval '1 minute'))",
        id(),
        hash(token),
        kind,
        user,
        inquiry,
        email,
        closure ? 15 : 1440);
    enqueue(
        email,
        closure ? "Confirm closing your getLancer account" : "Confirm your getLancer request",
        (closure
                ? "You requested account closure. Open this link and explicitly confirm to hide"
                    + " your profile and showcases and sign out all devices.\n"
                : "Open this link to continue:\n")
            + origin
            + "/confirm#token="
            + token
            + "\n\nThis link expires in "
            + (closure ? "15 minutes" : "24 hours")
            + ". If you did not request this, ignore this email.");
  }

  public void notify(UUID user, String title) {
    db.update(
        "INSERT INTO notifications(id,user_id,title) VALUES(?,?,?)",
        id(),
        user,
        title.length() > 200 ? title.substring(0, 197) + "..." : title);
    String email = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, user);
    enqueue(email, "An update from getLancer", title + "\n" + origin + "/workspace");
  }

  public void notifyClient(String email, String title) {
    for (UUID user :
        db.queryForList(
            "SELECT id FROM users WHERE email=? AND email_verified_at IS NOT NULL AND"
                + " account_status='ACTIVE'",
            UUID.class,
            email))
      db.update(
          "INSERT INTO notifications(id,user_id,title) VALUES(?,?,?)",
          id(),
          user,
          title.length() > 200 ? title.substring(0, 197) + "..." : title);
    enqueue(email, "Your getLancer request has an update", title + "\n" + origin + "/workspace");
  }

  @Scheduled(fixedDelay = 10000)
  public void deliver() {
    if (!jobsEnabled) return;
    tx.executeWithoutResult(
        s -> {
          var jobs =
              db.queryForList(
                  "SELECT id,recipient,subject,body,attempts,next_attempt_at,sent_at,created_at FROM email_outbox WHERE sent_at IS NULL AND attempts<8 AND"
                      + " next_attempt_at<=now() ORDER BY created_at LIMIT 10 FOR UPDATE SKIP"
                      + " LOCKED");
          for (var j : jobs) {
            try {
              SimpleMailMessage m = new SimpleMailMessage();
              m.setFrom(from);
              m.setTo((String) j.get("recipient"));
              m.setSubject((String) j.get("subject"));
              m.setText((String) j.get("body"));
              sender.send(m);
              db.update(
                  "UPDATE email_outbox SET sent_at=now(),body='[delivered]' WHERE id=?",
                  j.get("id"));
            } catch (Exception e) {
              db.update(
                  "UPDATE email_outbox SET attempts=attempts+1,next_attempt_at=now()+interval '5"
                      + " minutes' WHERE id=?",
                  j.get("id"));
              org.slf4j.LoggerFactory.getLogger(Mail.class)
                  .warn("email_delivery_failed job={}", j.get("id"));
            }
          }
        });
  }
}
