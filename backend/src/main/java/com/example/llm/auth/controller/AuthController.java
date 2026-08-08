package com.example.llm.auth.controller;

import com.example.llm.auth.api.AuthDtos;
import com.example.llm.auth.domain.AuthModels;
import com.example.llm.auth.security.AuthCookieManager;
import com.example.llm.auth.security.ClientRequestMetadataFactory;
import com.example.llm.auth.service.AuthApplicationService;
import com.example.llm.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {
    private final AuthApplicationService authService;
    private final ClientRequestMetadataFactory metadataFactory;
    private final AuthCookieManager cookieManager;

    public AuthController(
            AuthApplicationService authService,
            ClientRequestMetadataFactory metadataFactory,
            AuthCookieManager cookieManager) {
        this.authService = authService;
        this.metadataFactory = metadataFactory;
        this.cookieManager = cookieManager;
    }

    @PostMapping("/registration-challenges")
    public ResponseEntity<AuthDtos.RegistrationChallengeResponse> createRegistrationChallenge(
            @Valid @RequestBody AuthDtos.RegistrationChallengeRequest request,
            HttpServletRequest servletRequest) {
        AuthDtos.RegistrationChallengeResponse response = authService.createRegistrationChallenge(
                request, metadataFactory.from(servletRequest, request.deviceId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/registration-challenges/{challengeId}/verify-email")
    public AuthDtos.VerificationProofResponse verifyRegistrationEmail(
            @PathVariable String challengeId,
            @Valid @RequestBody AuthDtos.VerifyEmailRequest request) {
        return authService.verifyRegistrationEmail(challengeId, request);
    }

    @PostMapping("/password-reset-challenges")
    public ResponseEntity<AuthDtos.PasswordResetChallengeResponse> createPasswordResetChallenge(
            @Valid @RequestBody AuthDtos.PasswordResetChallengeRequest request,
            HttpServletRequest servletRequest) {
        AuthDtos.PasswordResetChallengeResponse response = authService.createPasswordResetChallenge(
                request, metadataFactory.from(servletRequest, request.deviceId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/password-reset-challenges/{challengeId}/verify-email")
    public AuthDtos.PasswordResetProofResponse verifyPasswordResetEmail(
            @PathVariable String challengeId,
            @Valid @RequestBody AuthDtos.PasswordResetVerifyEmailRequest request,
            HttpServletRequest servletRequest) {
        return authService.verifyPasswordResetEmail(
                challengeId,
                request,
                metadataFactory.from(servletRequest, request.deviceId()));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AuthDtos.PasswordResetRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        authService.resetPassword(
                request, metadataFactory.from(servletRequest, request.deviceId()));
        cookieManager.clear(servletResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.SessionResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthModels.IssuedSession issued = authService.register(
                request, metadataFactory.from(servletRequest, request.deviceId()));
        cookieManager.writeIssued(servletResponse, issued);
        return ResponseEntity.status(HttpStatus.CREATED).body(issued.response());
    }

    @PostMapping("/login")
    public AuthDtos.SessionResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthModels.IssuedSession issued = authService.login(
                request, metadataFactory.from(servletRequest, request.deviceId()));
        cookieManager.writeIssued(servletResponse, issued);
        return issued.response();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.logout(UserContext.requireSession());
        cookieManager.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public AuthDtos.SessionResponse session() {
        return UserContext.requireSession().response();
    }

    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(HttpServletResponse response) {
        String token = authService.refreshCsrf(UserContext.requireSession());
        cookieManager.writeCsrf(response, token);
        response.setHeader("Cache-Control", "no-store");
        return new AuthDtos.CsrfResponse(token);
    }
}
