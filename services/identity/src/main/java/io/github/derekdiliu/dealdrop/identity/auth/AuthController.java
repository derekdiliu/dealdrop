package io.github.derekdiliu.dealdrop.identity.auth;

import io.github.derekdiliu.dealdrop.identity.user.User;
import io.github.derekdiliu.dealdrop.identity.user.UserAccount;
import io.github.derekdiliu.dealdrop.identity.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/identity")
class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttempts loginAttempts;

    AuthController(UserService userService, AuthenticationManager authenticationManager, TokenService tokenService,
                   RefreshTokenService refreshTokens, LoginAttempts loginAttempts) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.register(request.email(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        loginAttempts.record(request.email());
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        loginAttempts.reset(request.email());
        UserAccount account = (UserAccount) authentication.getPrincipal();
        return tokenService.issue(account, refreshTokens.startFamily(account.id()));
    }

    /** Swaps a refresh token for a new access token and refresh token. The old refresh token is used up. */
    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(request.refreshToken())
                .orElseThrow(AuthController::signInAgain);
        UserAccount account = userService.findAccount(rotated.userId()).orElseThrow(AuthController::signInAgain);
        return tokenService.issue(account, rotated.next());
    }

    /** Ends the sign-in the refresh token belongs to. Access tokens already issued last until they expire. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.findById(UUID.fromString(jwt.getSubject()))
                .map(UserResponse::from)
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail loginFailed() {
        // Same response for unknown email and wrong password, so accounts cannot be enumerated via login.
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @ExceptionHandler(LoginAttempts.TooManyAttempts.class)
    ResponseEntity<ProblemDetail> tooManyAttempts(LoginAttempts.TooManyAttempts e) {
        long minutes = Math.max(1, (e.retryAfter().toSeconds() + 59) / 60);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many sign-in attempts. Please try again in " + minutes + (minutes == 1 ? " minute." : " minutes."));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfter().toSeconds()))
                .body(problem);
    }

    private static ErrorResponseException signInAgain() {
        return new ErrorResponseException(HttpStatus.UNAUTHORIZED,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Please sign in again."), null);
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            // BCrypt only uses the first 72 bytes of a password.
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {
    }

    record UserResponse(UUID id, String email, String displayName, String role) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
        }
    }
}
