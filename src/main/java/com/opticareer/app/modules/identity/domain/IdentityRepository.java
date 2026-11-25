package com.opticareer.app.modules.identity.domain;

import java.time.Instant;
import java.util.*;

public interface IdentityRepository {
  void lockEmail(String email);

  Optional<User> byEmail(String email);

  Optional<User> byId(UUID id);

  User lockUser(UUID id);

  void create(User user);

  void password(UUID owner, String hash);

  void verify(UUID owner);

  void block(UUID owner);

  void family(UUID id, UUID owner, Instant expires, String device, String ip, String agent);

  boolean familyActive(UUID family, UUID owner);

  void lockFamily(UUID family);

  void revoke(UUID family, UUID owner);

  void revokeAll(UUID owner);

  void session(Session session, String hash);

  Optional<Session> session(String hash);

  void rotate(UUID id);

  List<Map<String, Object>> families(UUID owner);

  void token(UUID id, UUID owner, String hash, String purpose, Instant expires);

  Optional<UUID> tokenOwner(String hash, String purpose, Instant now);

  UUID consume(String hash, String purpose, Instant now);

  void invalidateTokens(UUID owner, String purpose, Instant now);

  List<UUID> activeUsers(UUID cursor, int size);

  List<UUID> recoveryUsers(UUID cursor, int size);

  boolean isAdmin(UUID owner);

  void erase(UUID owner);

  Optional<UUID> oauthUser(String provider, String subject);

  void linkOAuth(UUID id, UUID owner, String provider, String subject);

  void recordOAuthChallenge(String tokenHash, Instant expiresAt);

  void consumeOAuthChallenge(String tokenHash, Instant now);
}
