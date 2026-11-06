package com.getlancer.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getlancer.shared.ApiError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RefundReconciliationTest {
  private final RazorpayClient provider=mock(RazorpayClient.class);
  private final ObjectMapper json=new ObjectMapper();
  private ObjectNode refund(String id,String status){return json.valueToTree(Map.of("entity","refund","id",id,"payment_id","pay_fixture123","amount",2500,"currency","INR","status",status));}
  private Map<String,Object> event(String id){return Map.of("event_id","event_"+id,"refund_id",id);}

  @Test void onlyExactTerminalRefundFactsResolveTheirSnapshottedReceipts(){
    when(provider.refund("rfnd_pending123")).thenReturn(refund("rfnd_pending123","pending"));
    when(provider.refund("rfnd_failed123")).thenReturn(refund("rfnd_failed123","failed"));
    when(provider.refund("rfnd_processed123")).thenReturn(refund("rfnd_processed123","processed"));
    var result=RefundReconciliation.observe(provider,List.of(event("rfnd_pending123"),event("rfnd_failed123"),event("rfnd_processed123")),"pay_fixture123",10000);
    assertEquals(3,result.refunds().size());assertEquals(List.of("event_rfnd_failed123","event_rfnd_processed123"),result.terminalEvents());
  }
  @Test void missingHistoricalReferenceIsNotInventedOrCleared(){
    ApiError error=assertThrows(ApiError.class,()->RefundReconciliation.observe(provider,List.of(Map.of("event_id","legacy")),"pay_fixture123",10000));
    assertEquals("REFUND_RECONCILIATION_REQUIRED",error.code);verifyNoInteractions(provider);
  }
  @Test void rejectsWrongIdentityMembershipAmountStatusAndCurrency(){
    for(String field:List.of("id","payment_id","amount","status","currency","entity")){
      var value=refund("rfnd_fixture123","processed");
      if(field.equals("amount"))value.put(field,10001);else value.put(field,"invalid");
      when(provider.refund("rfnd_fixture123")).thenReturn(value);
      assertEquals("PROVIDER_MISMATCH",assertThrows(ApiError.class,()->RefundReconciliation.observe(provider,List.of(event("rfnd_fixture123")),"pay_fixture123",10000)).code);
    }
  }
  @Test void providerReadsAreBoundedAndDoNotClearUnobservedReceipts(){
    var events=new ArrayList<Map<String,Object>>();
    for(int i=0;i<9;i++){String id="rfnd_bounded"+i;events.add(event(id));when(provider.refund(id)).thenReturn(refund(id,"failed"));}
    var result=RefundReconciliation.observe(provider,events,"pay_fixture123",10000);
    assertEquals(8,result.refunds().size());assertEquals(8,result.terminalEvents().size());verify(provider,never()).refund("rfnd_bounded8");
  }
}
