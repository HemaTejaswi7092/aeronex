package com.aeronex.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.user.Role;
import com.aeronex.user.User;
import com.aeronex.user.UserRepository;

/**
 * Ensures at least one ADMIN account exists at startup, since user creation is
 * itself ADMIN-gated. Idempotent: never touches an existing admin's credentials
 * on subsequent restarts, it only acts when no ADMIN exists yet.
 */
@Component
public class AdminUserBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrapper.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminUserBootstrapper(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  @Value("${aeronex.security.admin.username}") String adminUsername,
                                  @Value("${aeronex.security.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.findByRole(Role.ADMIN).isEmpty()) {
            return;
        }
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        User admin = new User(adminUsername, passwordEncoder.encode(adminPassword), Role.ADMIN, true);
        userRepository.save(admin);
        log.info("Bootstrapped initial admin user '{}'", adminUsername);
    }
}
