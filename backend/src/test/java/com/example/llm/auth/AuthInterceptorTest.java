package com.example.llm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.api.AuthDtos;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.domain.AuthModels;
import com.example.llm.auth.security.AuthCookieManager;
import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.auth.service.AuthApplicationService;
import com.example.llm.common.UserContext;
import com.example.llm.interceptor.AuthInterceptor;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {
    private AuthApplicationService authService;
    private AuthCrypto crypto;
    private AuthCookieManager cookieManager;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setHashSecret("interceptor-test-auth-hmac-secret-at-least-32-characters");
        crypto = new AuthCrypto(properties);
        cookieManager = new AuthCookieManager(properties);
        authService = mock(AuthApplicationService.class);
        interceptor = new AuthInterceptor(
                authService, cookieManager, crypto, new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        UserContext.remove();
    }

    @Test
    void requestWithoutValidSessionGetsStructuredUnauthorizedResponse() throws Exception {
        when(authService.authenticate(null)).thenThrow(new AuthApiException(
                HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "登录状态已失效，请重新登录。"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_INVALID").doesNotContain("token");
    }

    @Test
    void mutatingRequestRequiresMatchingCsrfHeader() throws Exception {
        AuthModels.AuthenticatedSession session = session(null, null);
        when(authService.authenticate("session-token")).thenReturn(session);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/auth/logout");
        request.setCookies(new Cookie("EXAMINSIGHT_SESSION", "session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_VALIDATION_FAILED");
    }

    @Test
    void validCsrfPassesAndRotatedSessionReplacesBothCookies() throws Exception {
        String csrf = "csrf-token";
        AuthModels.AuthenticatedSession session = session("rotated-session", "rotated-csrf");
        session = new AuthModels.AuthenticatedSession(
                session.sessionId(), session.sessionExternalId(), session.userId(),
                session.userExternalId(), crypto.digest("csrf-token", csrf),
                session.rotatedSessionToken(), session.rotatedCsrfToken(), session.response());
        when(authService.authenticate("session-token")).thenReturn(session);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/auth/logout");
        request.setCookies(new Cookie("EXAMINSIGHT_SESSION", "session-token"));
        request.addHeader("X-CSRF-Token", csrf);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(UserContext.getUserId()).isEqualTo(7L);
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(value -> value.contains("EXAMINSIGHT_SESSION=rotated-session") && value.contains("HttpOnly"))
                .anyMatch(value -> value.contains("XSRF-TOKEN=rotated-csrf"));
    }

    private AuthModels.AuthenticatedSession session(String rotatedSession, String rotatedCsrf) {
        AuthDtos.SessionResponse response = new AuthDtos.SessionResponse(
                "01AUTHUSER0000000000000000",
                "student@example.com",
                "王同学",
                "PRIMARY",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(86400));
        return new AuthModels.AuthenticatedSession(
                11L,
                "01AUTHSESSION0000000000000",
                7L,
                response.userId(),
                crypto.digest("csrf-token", "expected-csrf"),
                rotatedSession,
                rotatedCsrf,
                response);
    }
}
