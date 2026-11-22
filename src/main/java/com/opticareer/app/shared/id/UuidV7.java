package com.opticareer.app.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static long lastMillis;
  private static int sequence;

  private UuidV7() {}

  public static synchronized UUID next() {
    long now = System.currentTimeMillis();
    if (now > lastMillis) {
      lastMillis = now;
      sequence = RANDOM.nextInt(4096);
    } else if (++sequence > 4095) {
      lastMillis++;
      sequence = 0;
    }
    return new UUID(
        (lastMillis << 16) | 0x7000L | sequence,
        (RANDOM.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L);
  }
}
