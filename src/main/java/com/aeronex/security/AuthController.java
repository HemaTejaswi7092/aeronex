package com.aeronex.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aeronex.security.dto.LoginRequest;
import com.aeronex.security.dto.LoginResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        AeronexUserDetails userDetails = (AeronexUserDetails) authentication.getPrincipal();
        JwtService.GeneratedToken generatedToken = jwtService.generateToken(userDetails);

        return new LoginResponse(
                generatedToken.token(),
                "Bearer",
                generatedToken.expiresAt(),
                userDetails.getUsername(),
                userDetails.getUser().getRole()
        );
    }
}
