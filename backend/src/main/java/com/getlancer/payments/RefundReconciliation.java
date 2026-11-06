package com.getlancer.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.getlancer.shared.ApiError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Verifies only a bounded snapshot of accepted receipts; later events stay held. */
public final class RefundReconciliation {
  private RefundReconciliation() {}
  public record Observation(Map<String,JsonNode> refunds,List<String> terminalEvents) {}

  public static Observation observe(RazorpayClient provider,List<Map<String,Object>> events,
      String payment,long purchaseAmount) {
    var refunds=new LinkedHashMap<String,JsonNode>();
    var terminal=new ArrayList<String>();
    Instant started=Instant.now();
    for(var event:events) {
      if(Instant.now().isAfter(started.plusSeconds(90)))throw recovery();
      String id=Objects.toString(event.get("refund_id"),"");
      if(id.isBlank())throw recovery();
      RazorpayClient.providerId(id,"rfnd_");
      JsonNode refund=refunds.get(id);
      if(refund==null) {
        if(refunds.size()>=8)break;
        refund=provider.refund(id);
        if(!"refund".equals(refund.path("entity").asText())||!id.equals(refund.path("id").asText())
            ||!payment.equals(refund.path("payment_id").asText())||!"INR".equals(refund.path("currency").asText())
            ||!Set.of("pending","processed","failed").contains(refund.path("status").asText()))throw mismatch();
        var amount=refund.path("amount");
        if(!amount.isIntegralNumber()||!amount.canConvertToLong()||amount.longValue()<=0||amount.longValue()>purchaseAmount)throw mismatch();
        refunds.put(id,refund);
      }
      if(!"pending".equals(refund.path("status").asText()))terminal.add(Objects.toString(event.get("event_id")));
    }
    return new Observation(refunds,terminal);
  }
  private static ApiError mismatch() {return new ApiError(409,"PROVIDER_MISMATCH","Provider refund facts do not match this payment and frozen amount.");}
  private static ApiError recovery() {return new ApiError(409,"REFUND_RECONCILIATION_REQUIRED","Pending refund evidence needs its original signed payload and exact terminal provider confirmation. Access remains held.");}
}
