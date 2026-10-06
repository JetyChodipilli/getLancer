package com.getlancer.responses;

public record MutationResponse(boolean ok) {
  public static MutationResponse success() { return new MutationResponse(true); }
}
