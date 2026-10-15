package com.getlancer.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedDnsTest {
  ThreadPoolExecutor executor() {
    return new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
      Thread thread = new Thread(task, "dns-regression"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
  }

  @Test void anUninterruptibleLookupCannotCreateExtraWorkersOrQueuedRequests() throws Exception {
    var executor = executor();
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IOException.class,
          () -> BoundedDns.resolve(executor, () -> {
            entered.countDown();
            while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) { }
            return new InetAddress[0];
          }, 500)));
      assertEquals(0, entered.getCount());
      assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
        for (int i = 0; i < 100; i++) assertThrows(IOException.class,
            () -> BoundedDns.resolve(executor, () -> new InetAddress[0], 500));
      });
      assertEquals(1, executor.getPoolSize()); assertEquals(0, executor.getQueue().size());
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test void healthyResolutionLookupFailureAndInterruptedCallerHaveDistinctSafeOutcomes() throws Exception {
    var executor = executor();
    try {
      var address = InetAddress.getByAddress(new byte[] {8, 8, 8, 8});
      assertEquals(address, BoundedDns.resolve(executor, () -> new InetAddress[] {address}, 1000)[0]);
      assertThrows(IOException.class, () -> BoundedDns.resolve(executor,
          () -> { throw new IOException("synthetic resolver failure"); }, 1000));
      Thread.currentThread().interrupt();
      assertThrows(IOException.class, () -> BoundedDns.resolve(executor, () -> new InetAddress[] {address}, 1000));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); executor.shutdownNow(); }
  }

  @Test void urlValidationKeepsLexicalAndEveryAddressControlsWithoutLiveDns() throws Exception {
    var publicIp = InetAddress.getByAddress(new byte[] {8, 8, 8, 8});
    var privateIp = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    var calls = new AtomicInteger();
    Rules.AddressLookup healthy = host -> { calls.incrementAndGet(); return new InetAddress[] {publicIp}; };
    Rules.safeUrl(null, healthy); Rules.safeUrl("", healthy); assertEquals(0, calls.get());
    Rules.safeUrl("https://example.test/path", healthy); assertEquals(1, calls.get());
    for (String raw : new String[] {"http://example.test", "https://u:p@example.test", "https://localhost",
        "https://example.internal", "https://127.0.0.1", "https://[::1]", "https://example.test:8443"})
      assertEquals("UNSAFE_EXTERNAL_URL", assertThrows(IllegalArgumentException.class,
          () -> Rules.safeUrl(raw, healthy)).getMessage());
    assertEquals(1, calls.get());
    assertThrows(IllegalArgumentException.class, () -> Rules.safeUrl("https://example.test",
        host -> new InetAddress[] {publicIp, privateIp}));
    assertThrows(IllegalArgumentException.class, () -> Rules.safeUrl("https://example.test", host -> new InetAddress[0]));
    assertThrows(IllegalArgumentException.class, () -> Rules.safeUrl("https://example.test",
        host -> { throw new IOException("synthetic timeout"); }));
  }
}
