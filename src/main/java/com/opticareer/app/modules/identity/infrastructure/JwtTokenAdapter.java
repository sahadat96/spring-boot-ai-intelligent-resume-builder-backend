package com.opticareer.app.modules.identity.infrastructure;

import com.opticareer.app.modules.identity.application.IdentityPorts;
import com.opticareer.app.platform.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenAdapter implements IdentityPorts.Tokens {
  private final JwtEncoder encoder;
  private final Clock clock;
  private final AppProperties properties;

  public JwtTokenAdapter(JwtEncoder encoder, Clock clock, AppProperties properties) {
    this.encoder = encoder;
    this.clock = clock;
    this.properties = properties;
  }

  @Override
  public String access(UUID owner, UUID family) {
    Instant now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.security().issuer())
            .subject(owner.toString())
            .audience(List.of("opticareer-api"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(properties.security().accessMinutes() * 60L))
            .id(UUID.randomUUID().toString())
            .claim("sid", family.toString())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
