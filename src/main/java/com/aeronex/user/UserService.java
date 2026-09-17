package com.aeronex.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.user.dto.UserCreateRequest;
import com.aeronex.user.dto.UserResponse;
import com.aeronex.user.exception.DuplicateUsernameException;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException("Username " + request.username() + " already exists");
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.role(),
                true
        );

        User saved = userRepository.saveAndFlush(user);
        return toResponse(saved);
    }

    static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
