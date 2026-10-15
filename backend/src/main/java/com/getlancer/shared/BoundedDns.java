package com.getlancer.shared;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A deadline alone cannot stop native DNS; also bound workers and reject any backlog. */
public final class BoundedDns {
  private static final ThreadPoolExecutor URL_LOOKUPS = new ThreadPoolExecutor(
      0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
        Thread thread = new Thread(task, "external-url-dns");
        thread.setDaemon(true);
        return thread;
      }, new ThreadPoolExecutor.AbortPolicy());

  private BoundedDns() {}

  public static InetAddress[] resolve(String host) throws IOException {
    return resolve(URL_LOOKUPS, () -> InetAddress.getAllByName(host), 2000);
  }

  public static InetAddress[] resolve(ThreadPoolExecutor executor,
      Callable<InetAddress[]> lookup, long timeoutMillis) throws IOException {
    Future<InetAddress[]> task;
    try { task = executor.submit(lookup); }
    catch (RejectedExecutionException exception) { throw new IOException("DNS resolver busy", exception); }
    try { return task.get(timeoutMillis, TimeUnit.MILLISECONDS); }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("DNS interrupted", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IOException("DNS failed or timed out", exception);
    } finally { task.cancel(true); }
  }
}
