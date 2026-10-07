package com.getlancer.responses;

import java.util.List;
import java.util.UUID;

/** Authenticated account view; excludes credential, session and OAuth material. */
public record UserResponse(UUID id, String email, String displayName,
    AccountExportResponse.Profile profile, List<String> roles, boolean emailVerified, Integer activeSlotLimit) {}
