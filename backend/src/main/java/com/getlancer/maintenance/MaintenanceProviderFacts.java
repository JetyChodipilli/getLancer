package com.getlancer.maintenance;

import static com.getlancer.maintenance.MaintenanceRepository.mismatch;
import static com.getlancer.maintenance.MaintenanceRepository.number;

import com.fasterxml.jackson.databind.JsonNode;
import com.getlancer.payments.RazorpayClient;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fail-closed parsing of provider facts; no browser or webhook status is payment authority. */
final class MaintenanceProviderFacts {
  static final Set<String> STATES=Set.of("created","authenticated","active","pending","halted","cancelled","completed","expired");
  static long integer(JsonNode node,String field) {var v=node.path(field);if(!v.isIntegralNumber()||!v.canConvertToLong())throw mismatch();return v.longValue();}
  static String id(JsonNode n,String field,String prefix) {String value=n.path(field).asText();try{return RazorpayClient.providerId(value,prefix);}catch(RuntimeException e){throw mismatch();}}
  static void equal(boolean fact) {if(!fact)throw mismatch();}
  static void notes(Map<String,Object> s,JsonNode n) {equal(n.path("notes").path("maintenance_attempt").asText().equals(s.get("id").toString())&&n.path("notes").path("terms_hash").asText().equals(s.get("digest")));}
  static String plan(Map<String,Object> s,JsonNode n) {
    String id=id(n,"id","plan_");equal(n.path("entity").asText().equals("plan")&&n.path("period").asText().equals("monthly")&&integer(n,"interval")==1&&integer(n.path("item"),"amount")==number(s,"amount_minor")&&n.path("item").path("currency").asText().equals("INR"));notes(s,n);
    if(s.get("provider_plan_id")!=null)equal(id.equals(s.get("provider_plan_id")));return id;
  }
  static String subscription(Map<String,Object> s,JsonNode n) {
    String id=id(n,"id","sub_");equal(n.path("entity").asText().equals("subscription")&&n.path("plan_id").asText().equals(s.get("provider_plan_id"))&&integer(n,"quantity")==1&&integer(n,"total_count")==number(s,"total_cycles")&&STATES.contains(n.path("status").asText()));notes(s,n);
    if(s.get("provider_subscription_id")!=null)equal(id.equals(s.get("provider_subscription_id")));return id;
  }
  static boolean terminal(String status) {return Set.of("cancelled","completed","expired").contains(status.toLowerCase(Locale.ROOT));}
  record Invoice(String id,String payment,String order,long amount,Instant start,Instant end) {}
  static Invoice invoice(Map<String,Object> s,JsonNode n) {
    String id=id(n,"id","inv_"),payment=id(n,"payment_id","pay_"),order=id(n,"order_id","order_");
    equal(n.path("entity").asText().equals("invoice")&&n.path("subscription_id").asText().equals(s.get("provider_subscription_id"))&&n.path("status").asText().equals("paid")&&n.path("currency").asText().equals("INR")&&integer(n,"amount")==number(s,"amount_minor")&&integer(n,"amount_paid")==number(s,"amount_minor")&&integer(n,"amount_due")==0&&n.path("partial_payment").isBoolean()&&!n.path("partial_payment").booleanValue());
    var lines=n.path("line_items");equal(lines.isArray()&&lines.size()==1);var line=lines.get(0);equal(line.path("type").asText().equals("plan")&&integer(line,"quantity")==1&&integer(line,"amount")==number(s,"amount_minor")&&line.path("currency").asText().equals("INR"));
    long start=integer(n,"billing_start"),end=integer(n,"billing_end");equal(start>0&&end>start&&end-start<=40*86400L);
    return new Invoice(id,payment,order,number(s,"amount_minor"),Instant.ofEpochSecond(start),Instant.ofEpochSecond(end));
  }
  static long payment(Invoice i,JsonNode n) {
    equal(id(n,"id","pay_").equals(i.payment)&&n.path("entity").asText().equals("payment")&&n.path("order_id").asText().equals(i.order)&&Set.of("captured","refunded").contains(n.path("status").asText())&&n.path("captured").isBoolean()&&n.path("captured").booleanValue()&&integer(n,"amount")==i.amount&&n.path("currency").asText().equals("INR")&&(n.path("invoice_id").isMissingNode()||n.path("invoice_id").isNull()||n.path("invoice_id").asText().equals(i.id)));
    long refunded=integer(n,"amount_refunded");equal(refunded>=0&&refunded<=i.amount);var status=n.path("refund_status");equal((status.isMissingNode()||status.isNull())||(refunded>0&&Set.of("partial","full").contains(status.asText())));equal(!n.path("status").asText().equals("refunded")||refunded==i.amount);return refunded;
  }
  record Refunds(long total,boolean pending) {}
  static Refunds refunds(Invoice i,List<JsonNode> rows,long capturedTotal) {long total=0;boolean pending=false;var ids=new HashSet<String>();for(var n:rows){equal(ids.add(id(n,"id","rfnd_"))&&n.path("entity").asText().equals("refund")&&n.path("payment_id").asText().equals(i.payment)&&n.path("currency").asText().equals("INR"));long amount=integer(n,"amount");equal(amount>0&&amount<=i.amount);String status=n.path("status").asText();equal(Set.of("pending","processed","failed").contains(status));if(status.equals("processed"))total=Math.addExact(total,amount);if(status.equals("pending"))pending=true;}equal(total<=i.amount);return new Refunds(Math.max(total,capturedTotal),pending);}
  static void dispute(Invoice i,JsonNode n) {id(n,"id","disp_");equal(n.path("payment_id").asText().equals(i.payment)&&n.path("currency").asText().equals("INR")&&Set.of("open","under_review","won","lost","closed").contains(n.path("status").asText()));long amount=integer(n,"amount"),deducted=integer(n,"amount_deducted");equal(amount>0&&amount<=i.amount&&deducted>=0&&deducted<=amount);}
  static String transfer(Map<String,Object> s,Map<String,Object> p,JsonNode n) {
    String id=id(n,"id","trf_");equal(n.path("entity").asText().equals("transfer")&&n.path("source").asText().equals(p.get("payment_id"))&&n.path("recipient").asText().equals(s.get("account_id"))&&integer(n,"amount")==number(p,"amount_minor")&&n.path("currency").asText().equals("INR")&&n.path("notes").path("maintenance_route").asText().equals(p.get("transfer_key").toString())&&n.path("notes").path("invoice_id").asText().equals(p.get("provider_invoice_id")));
    if(p.get("transfer_id")!=null)equal(id.equals(p.get("transfer_id")));long reversed=integer(n,"amount_reversed");equal(reversed>=0&&reversed<=number(p,"amount_minor"));String status=n.path("status").asText();equal(Set.of("created","pending","processed","failed","reversed","partially_reversed").contains(status));return reversed>0?"REVERSED":status.toUpperCase(Locale.ROOT);
  }
}
