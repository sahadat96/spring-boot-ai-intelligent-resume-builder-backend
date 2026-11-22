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

  void mfa(UUID owner, String encrypted);

  Optional<Mfa> mfa(UUID owner);

  void confirmMfa(UUID owner, long counter);

  void mfaCounter(UUID owner, long counter);

  void removeMfa(UUID owner);

  void challenge(String hash, Instant expires);

  boolean consumeChallenge(String hash, Instant now);

  Optional<UUID> googleUser(String subject);

  void googleLink(UUID owner, String subject);

  java.util.List<UUID> activeUsers(UUID cursor, int size);

  java.util.List<UUID> recoveryUsers(UUID cursor, int size);

  boolean isAdmin(UUID owner);

  void erase(UUID owner);

  record Mfa(String encrypted, boolean confirmed, long lastCounter) {}
}
