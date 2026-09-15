package com.getlancer;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
@Component class RateLimits {
 final JdbcTemplate db;
 RateLimits(JdbcTemplate db){this.db=db;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public boolean allow(String key,int limit){
  Integer hits=db.queryForObject("INSERT INTO rate_buckets(bucket,hits,expires_at) VALUES(?,1,now()+interval '1 minute') ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN rate_buckets.expires_at<now() THEN 1 ELSE rate_buckets.hits+1 END,expires_at=CASE WHEN rate_buckets.expires_at<now() THEN now()+interval '1 minute' ELSE rate_buckets.expires_at END RETURNING hits",Integer.class,Support.hash(key));
  return hits!=null&&hits<=limit;
 }
}
