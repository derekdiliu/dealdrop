package io.github.derekdiliu.dealdrop.identity.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the admin account from configuration on startup, so there is always a way into the
 * back office without a manual database edit.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserService userService;
    private final AdminProperties properties;

    AdminBootstrap(UserService userService, AdminProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            return;
        }
        userService.ensureAdmin(properties.email(), properties.password());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @ConfigurationProperties("dealdrop.admin")
    record AdminProperties(String email, String password) {
    }
}
