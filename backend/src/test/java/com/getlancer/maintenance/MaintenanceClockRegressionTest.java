package com.getlancer.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.payments.PaymentRepository;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

// Regression: ISSUE-004 — JVM clock skew excluded retained refund evidence from reconciliation.
// Found by /qa on 2026-10-13. Report: docs/audits/2026-10-13-local-audit.md
class MaintenanceClockRegressionTest {
  @Test void unresolvedRefundsUseDatabaseTimeWithEitherDirectionOfJvmSkew() {
    for (Instant cutoff : new Instant[] {Instant.parse("1900-01-01T00:00:00Z"),
        Instant.parse("2100-01-01T00:00:00Z")}) {
      UUID id = UUID.randomUUID();
      var repository = mock(MaintenanceRepository.class);
      var access = mock(MaintenanceAccess.class);
      var provider = mock(RazorpayClient.class);
      var manager = mock(PlatformTransactionManager.class);
      when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
      var snapshot = Map.<String, Object>of("id", id, "mode", "test",
          "reconcile_generation", 1L, "provider_subscription_id", "sub_clock_fixture");
      when(access.system(id)).thenReturn(snapshot);
      when(repository.billing(id, false)).thenReturn(snapshot);
      when(provider.mode()).thenReturn("test");
      when(repository.reconciliationCutoff()).thenReturn(cutoff);
      when(repository.unidentifiedRefundEvents(id, cutoff)).thenReturn(true);
      var service = new MaintenanceBillingService(repository, access,
          mock(PaymentRepository.class), provider, mock(Security.class), new ObjectMapper(),
          manager, true, true, "fixture-webhook-not-for-production", false);
      ApiError denial = assertThrows(ApiError.class, () -> service.reconcile(id));
      assertEquals("PROVIDER_INCOMPLETE", denial.code);
      verify(repository).unidentifiedRefundEvents(id, cutoff);
      verify(provider, never()).plan(any());
      verify(provider, never()).createPaymentTransfer(any(), any(), anyLong(), any(), any());
    }
  }
}
