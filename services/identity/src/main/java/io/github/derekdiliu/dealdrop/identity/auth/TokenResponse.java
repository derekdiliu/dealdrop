package io.github.derekdiliu.dealdrop.identity.auth;

record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
