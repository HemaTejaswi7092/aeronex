package com.aeronex.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.aeronex.user.dto.UserCreateRequest;
import com.aeronex.user.dto.UserResponse;
import com.aeronex.user.exception.DuplicateUsernameException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void createHashesPasswordAndNeverStoresPlaintext() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", java.util.UUID.randomUUID());
            return user;
        });

        UserResponse response = userService.create(new UserCreateRequest("bob", "supersecret1", Role.OPS));

        assertThat(response.username()).isEqualTo("bob");
        assertThat(response.role()).isEqualTo(Role.OPS);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("supersecret1");
        assertThat(passwordEncoder.matches("supersecret1", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createThrowsWhenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new UserCreateRequest("bob", "supersecret1", Role.OPS)))
                .isInstanceOf(DuplicateUsernameException.class);
    }
}
