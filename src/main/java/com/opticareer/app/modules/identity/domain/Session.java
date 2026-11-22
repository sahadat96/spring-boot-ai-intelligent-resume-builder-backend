package com.opticareer.app.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record Session(UUID id, UUID owner, UUID family, Instant expiresAt, Instant rotatedAt) {}
