package io.github.derekdiliu.dealdrop.identity.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String rawPassword, String displayName) {
        return create(email, rawPassword, displayName, Role.CUSTOMER);
    }

    void ensureAdmin(String email, String rawPassword) {
        Optional<User> existing = users.findByEmail(normalizeEmail(email));
        if (existing.isEmpty()) {
            create(email, rawPassword, "Admin", Role.ADMIN);
            log.info("Created admin account {}", normalizeEmail(email));
        } else if (existing.get().getRole() != Role.ADMIN) {
            log.warn("Admin email {} belongs to a {} account; leaving it unchanged",
                    existing.get().getEmail(), existing.get().getRole());
        }
    }

    private User create(String email, String rawPassword, String displayName, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), displayName.strip(), role);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration for the same email.
            throw new EmailAlreadyRegisteredException();
        }
    }

    public Optional<User> findById(UUID id) {
        return users.findById(id);
    }

    public Optional<UserAccount> findAccount(UUID id) {
        return users.findById(id).map(UserAccount::from);
    }

    @Override
    public UserAccount loadUserByUsername(String email) {
        return users.findByEmail(normalizeEmail(email))
                .map(UserAccount::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account for this email"));
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
