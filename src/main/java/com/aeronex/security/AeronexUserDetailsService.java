package com.aeronex.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.user.User;
import com.aeronex.user.UserRepository;

@Service
public class AeronexUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AeronexUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AeronexUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username: " + username));
        return new AeronexUserDetails(user);
    }
}
