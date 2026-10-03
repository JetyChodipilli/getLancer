package com.getlancer.maintenance;

import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceAccess {
  final MaintenanceRepository repo; final DeliveryRepository delivery; final Security security;
  public MaintenanceAccess(MaintenanceRepository repo,DeliveryRepository delivery,Security security) {this.repo=repo;this.delivery=delivery;this.security=security;}
  Map<String,Object> offer(UUID id,HttpServletRequest request,String side,boolean eligible) {
    var before=repo.offer(id,false);
    var engagement=delivery.lockEngagement((UUID)before.get("engagement_id"),request,side);
    repo.lockActor(security.user(request));
    engagement=delivery.lockEngagement((UUID)before.get("engagement_id"),request,side);
    if(eligible) requireEligible(engagement);
    var row=repo.offer(id,true);row.put("side",engagement.get("side"));return row;
  }
  Map<String,Object> billing(UUID id,HttpServletRequest request,String side,boolean eligible) {
    var row=repo.billing(id,false);offer((UUID)row.get("offer_id"),request,side,eligible);return repo.billing(id,true);
  }
  Map<String,Object> system(UUID id) {var before=repo.billing(id,false);delivery.lockEngagementSystem((UUID)before.get("engagement_id"));return repo.billing(id,true);}
  boolean eligible(Map<String,Object> engagement) {return repo.eligible(engagement,true)&&delivery.allPaid((UUID)engagement.get("id"));}
  void requireEligible(Map<String,Object> engagement) {if(!eligible(engagement))throw new ApiError(409,"MAINTENANCE_UNAVAILABLE","Maintenance requires a completed, fully paid engagement with current eligible parties and no financial holds.");}
}
