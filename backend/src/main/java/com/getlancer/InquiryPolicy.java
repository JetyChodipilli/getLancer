package com.getlancer;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
final class InquiryPolicy {
 static void requireClear(Map<String,Object> inquiry){
  if(!Objects.toString(inquiry.get("moderation_status"),"").equals("CLEAR"))throw new ApiError(409,"INQUIRY_RESTRICTED","This inquiry is restricted pending moderation.");
 }
 static void requireSender(JdbcTemplate db,String email){
  if(db.queryForObject("SELECT count(*) FROM users WHERE email=? AND account_status<>'ACTIVE'",Integer.class,email)>0)throw new ApiError(403,"ACCOUNT_UNAVAILABLE","Unable to submit or confirm this request.");
 }
}
