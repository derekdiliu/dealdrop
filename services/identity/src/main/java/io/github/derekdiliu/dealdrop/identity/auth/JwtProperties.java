package io.github.derekdiliu.dealdrop.identity.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

@ConfigurationProperties("dealdrop.jwt")
public record JwtProperties(String issuer, Duration ttl, Resource privateKey, Resource publicKey) {
}
