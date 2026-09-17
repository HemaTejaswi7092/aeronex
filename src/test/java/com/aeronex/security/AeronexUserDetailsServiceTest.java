package com.aeronex.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.aeronex.user.Role;
import com.aeronex.user.User;
import com.aeronex.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AeronexUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private AeronexUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AeronexUserDetailsService(userRepository);
    }

    @Test
    void loadsExistingUserWithRoleBasedAuthority() {
        User user = new User("alice", "hashed", Role.ADMIN, true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        AeronexUserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void throwsWhenUsernameUnknown() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
