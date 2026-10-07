package com.getlancer.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Enforces a byte limit even when Content-Length is absent, chunked, or misleading. */
public final class BoundedRequest extends HttpServletRequestWrapper {
  public static final class TooLarge extends IOException {
    public TooLarge() { super("Request exceeds the byte limit"); }
  }
  private final long limit;
  private ServletInputStream bounded;
  public BoundedRequest(HttpServletRequest request, long limit) { super(request); this.limit = limit; }

  @Override public ServletInputStream getInputStream() throws IOException {
    if (bounded != null) return bounded;
    var original = super.getInputStream();
    bounded = new ServletInputStream() {
      private long bytes;
      private void count(int amount) throws TooLarge {
        if (amount > 0 && (bytes += amount) > limit) throw new TooLarge();
      }
      @Override public int read() throws IOException {
        int value = original.read(); count(value < 0 ? 0 : 1); return value;
      }
      @Override public int read(byte[] values, int offset, int length) throws IOException {
        int requested = (int) Math.min(length, Math.max(1, limit - bytes + 1));
        int read = original.read(values, offset, requested); count(read); return read;
      }
      @Override public boolean isFinished() { return original.isFinished(); }
      @Override public boolean isReady() { return original.isReady(); }
      @Override public void setReadListener(ReadListener listener) { original.setReadListener(listener); }
      @Override public void close() throws IOException { original.close(); }
    };
    return bounded;
  }

  @Override public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
