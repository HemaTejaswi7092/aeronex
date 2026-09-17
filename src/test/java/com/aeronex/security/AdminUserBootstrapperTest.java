package com.aeronex.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.aeronex.user.Role;
import com.aeronex.user.User;
import com.aeronex.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserBootstrapperTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminUserBootstrapper bootstrapper;

    @BeforeEach
    void setUp() {
        bootstrapper = new AdminUserBootstrapper(userRepository, passwordEncoder, "admin", "admin123");
    }

    @Test
    void createsAdminWhenNoneExists() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());
        when(userRepository.existsByUsername("admin")).thenReturn(false);

        bootstrapper.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("admin123", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void doesNotCreateDuplicateAdminWhenOneAlreadyExists() {
        User existingAdmin = new User("admin", "already-hashed", Role.ADMIN, true);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(existingAdmin));

        bootstrapper.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNotOverwriteExistingUserWithSameUsernameButDifferentRole() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        bootstrapper.run(null);

        verify(userRepository, never()).save(any());
    }
}
