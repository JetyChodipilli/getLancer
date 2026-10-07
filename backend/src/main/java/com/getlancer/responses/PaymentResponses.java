package com.getlancer.responses;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public payment contracts expose reconciliation facts, never provider credentials or raw payloads. */
public final class PaymentResponses {
  private PaymentResponses() {}

  public record Configuration(boolean enabled, String keyId, String mode, String reason) {
    public static Configuration from(Map<String, Object> row) {
      return new Configuration(Boolean.TRUE.equals(row.get("enabled")), ResponseRows.string(row,"keyId"),
          ResponseRows.string(row,"mode"), ResponseRows.string(row,"reason"));
    }
  }

  public record Summary(UUID id, UUID milestoneId, String status, Long amountMinor, String currency,
      String mode, String orderId, String paymentId, Long refundedMinor, String transferStatus,
      String settlementStatus, String attentionReason) {
    public static Summary fromPaymentRow(Map<String, Object> row) {
      return new Summary(ResponseRows.uuid(row,"id"), ResponseRows.uuid(row,"milestone_id"),
          ResponseRows.string(row,"status"), ResponseRows.integer64(row,"amount_minor"),
          ResponseRows.string(row,"currency"), ResponseRows.string(row,"mode"),
          ResponseRows.string(row,"order_id"), ResponseRows.string(row,"payment_id"),
          ResponseRows.integer64(row,"refunded_minor"), ResponseRows.string(row,"transfer_status"),
          ResponseRows.string(row,"settlement_status"), ResponseRows.string(row,"attention_reason"));
    }
    public static Summary fromSummaryRow(Map<String, Object> row) {
      return new Summary(ResponseRows.uuid(row,"id"), ResponseRows.uuid(row,"milestoneId"),
          ResponseRows.string(row,"status"), ResponseRows.integer64(row,"amountMinor"),
          ResponseRows.string(row,"currency"), ResponseRows.string(row,"mode"),
          ResponseRows.string(row,"orderId"), ResponseRows.string(row,"paymentId"),
          ResponseRows.integer64(row,"refundedMinor"), ResponseRows.string(row,"transferStatus"),
          ResponseRows.string(row,"settlementStatus"), ResponseRows.string(row,"attentionReason"));
    }
  }

  public record Checkout(UUID id, UUID milestoneId, String status, Long amountMinor, String currency,
      String mode, String orderId, String paymentId, Long refundedMinor, String transferStatus,
      String settlementStatus, String attentionReason, String keyId) {
    public static Checkout from(Map<String, Object> row, String keyId) {
      Summary summary = Summary.fromPaymentRow(row);
      return new Checkout(summary.id(),summary.milestoneId(),summary.status(),summary.amountMinor(),
          summary.currency(),summary.mode(),summary.orderId(),summary.paymentId(),summary.refundedMinor(),
          summary.transferStatus(),summary.settlementStatus(),summary.attentionReason(),keyId);
    }
  }

  public record Summaries(List<Summary> items) {}
  public record Accounts(List<Account> items) {}
  public record AttentionItems(List<Attention> items) {}
  public record WebhookReceipt(boolean received, boolean duplicate) {}

  public record Account(UUID id, UUID builderUserId, UUID teamId, String accountId, String mode,
      String providerStatus, Timestamp verifiedAt) {
    public static Account from(Map<String, Object> row) {
      return new Account(ResponseRows.uuid(row,"id"),ResponseRows.uuid(row,"builderUserId"),
          ResponseRows.uuid(row,"teamId"),ResponseRows.string(row,"accountId"),ResponseRows.string(row,"mode"),
          ResponseRows.string(row,"providerStatus"),ResponseRows.timestamp(row,"verifiedAt"));
    }
  }

  public record Attention(UUID id, UUID milestoneId, UUID engagementId, String status, Long amountMinor,
      String currency, String mode, String orderId, String accountId, String attentionReason,
      String transferStatus, String settlementStatus, Timestamp createdAt) {
    public static Attention from(Map<String, Object> row) {
      return new Attention(ResponseRows.uuid(row,"id"),ResponseRows.uuid(row,"milestoneId"),
          ResponseRows.uuid(row,"engagementId"),ResponseRows.string(row,"status"),
          ResponseRows.integer64(row,"amountMinor"),ResponseRows.string(row,"currency"),
          ResponseRows.string(row,"mode"),ResponseRows.string(row,"orderId"),ResponseRows.string(row,"accountId"),
          ResponseRows.string(row,"attentionReason"),ResponseRows.string(row,"transferStatus"),
          ResponseRows.string(row,"settlementStatus"),ResponseRows.timestamp(row,"createdAt"));
    }
  }
}
