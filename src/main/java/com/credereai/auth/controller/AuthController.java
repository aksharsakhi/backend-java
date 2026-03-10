package com.credereai.auth.controller;

import com.credereai.auth.models.AuthModels.CurrentUserResponse;
import com.credereai.auth.models.AuthModels.LoginRequest;
import com.credereai.auth.models.AuthModels.LoginResponse;
import com.credereai.auth.services.AuthService;
import com.credereai.module1.models.Responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
        public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword())
                .map(response -> ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                        .success(true)
                        .message("Login successful")
                        .data(response)
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.<LoginResponse>builder()
                                .success(false)
                                .message("Invalid username or password")
                                .build()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {

        return authService.getCurrentUser(token)
                .map(response -> ResponseEntity.ok(ApiResponse.<CurrentUserResponse>builder()
                        .success(true)
                        .message("Session valid")
                        .data(response)
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.<CurrentUserResponse>builder()
                                .success(false)
                                .message("Session expired or invalid")
                                .build()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        authService.logout(token);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Logged out")
                .build();
    }
}
