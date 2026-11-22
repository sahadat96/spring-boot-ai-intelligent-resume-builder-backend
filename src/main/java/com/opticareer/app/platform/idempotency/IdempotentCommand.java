package com.opticareer.app.platform.idempotency;

import com.opticareer.app.platform.crypto.FieldCipher;
import com.opticareer.app.platform.json.Json;
import com.opticareer.app.platform.persistence.*;
import com.opticareer.app.shared.error.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IdempotentCommand {
  private final IdempotencyJpaRepository records;
  private final BlockedOwnerJpaRepository blockedOwners;
  private final ErasureTombstoneJpaRepository tombstones;
  private final DatabaseLocks locks;
  private final TransactionTemplate tx;
  private final Json json;
  private final FieldCipher cipher;
  private final Clock clock;

  public IdempotentCommand(
      IdempotencyJpaRepository records,
      BlockedOwnerJpaRepository blockedOwners,
      ErasureTombstoneJpaRepository tombstones,
      DatabaseLocks locks,
      TransactionTemplate tx,
      Json json,
      FieldCipher cipher,
      Clock clock) {
    this.records = records;
    this.blockedOwners = blockedOwners;
    this.tombstones = tombstones;
    this.locks = locks;
    this.tx = tx;
    this.json = json;
    this.cipher = cipher;
    this.clock = clock;
  }

  public <T> T run(
      UUID owner,
      String key,
      String scope,
      Object input,
      Class<T> resultType,
      Supplier<T> operation) {
    if (key == null || !key.matches("[a-zA-Z0-9._:-]{8,100}"))
      throw DomainException.invalid("Provide an Idempotency-Key of 8 to 100 safe characters.");
    String hash = hash(json.canonical(input));
    String context = "idempotency:" + owner + ":" + scope + ":" + key;
    return tx.execute(
        status -> {
          locks.exclusive("privacy-owner:" + owner);
          locks.exclusive(context);
          if (blockedOwners.existsById(owner) || tombstones.existsById(hash(owner.toString())))
            throw new DomainException(ErrorCode.DELETION_PENDING, "Account deletion is pending.");
          var id = new IdempotencyId(owner, scope, key);
          var found = records.findById(id);
          Instant now = clock.instant();
          if (found.isPresent() && found.get().expiresAt.isAfter(now)) {
            var previous = found.get();
            if (!hash.equals(previous.requestHash))
              throw new DomainException(
                  ErrorCode.IDEMPOTENCY_CONFLICT, "This key was used for a different request.");
            return json.read(cipher.decrypt(previous.responseEnc, context), resultType);
          }
          T response = operation.get();
          var record =
              found.orElseGet(
                  () -> {
                    var e = new IdempotencyEntity();
                    e.id = id;
                    return e;
                  });
          record.requestHash = hash;
          record.responseEnc = cipher.encrypt(json.write(response), context);
          record.expiresAt = now.plus(Duration.ofHours(24));
          records.saveAndFlush(record);
          return response;
        });
  }

  public static String hash(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String hash(String value) {
    return hash(value.getBytes(StandardCharsets.UTF_8));
  }
}
