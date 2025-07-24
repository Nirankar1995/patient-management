package com.pm.authservice.controller;

import com.pm.authservice.dto.LoginRequestDTO;
import com.pm.authservice.dto.LoginResponseDTO;
import com.pm.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "User Login", description = "Authenticate user and return JWT token")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        Optional<String> token = authService.authenticate(loginRequest.getEmail(), loginRequest.getPassword());
        log.info("Generated token is {}", token.orElse("No token generated"));
        return token.map(s -> ResponseEntity.ok(new LoginResponseDTO(s))).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Operation(summary = "Validate Token")
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        // Authorization: Bearer <token>
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return authService.validateToken(authHeader.substring(7))
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

}
