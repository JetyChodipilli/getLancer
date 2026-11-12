package com.getlancer.payments;

/** Real provider outage exception for tests outside the adapter package. */
public final class ProviderFailureFixture {
  private ProviderFailureFixture() {}

  public static RazorpayClient.ProviderFailure unavailable() {
    return new RazorpayClient.ProviderFailure(false);
  }
}
