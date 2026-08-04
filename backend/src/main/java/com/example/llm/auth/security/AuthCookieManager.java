package com.example.llm.auth.security;

import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.domain.AuthModels;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class AuthCookieManager {
    private final AuthProperties properties;

    public AuthCookieManager(AuthProperties properties) {
        this.properties = properties;
    }

    public String readSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.getCookie().getName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public void writeIssued(HttpServletResponse response, AuthModels.IssuedSession issuedSession) {
        write(response, issuedSession.sessionToken(), issuedSession.csrfToken());
    }

    public void writeRotated(
            HttpServletResponse response,
            String sessionToken,
            String csrfToken) {
        write(response, sessionToken, csrfToken);
    }

    public void writeCsrf(HttpServletResponse response, String csrfToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(csrfToken, sessionMaxAge()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie("", Duration.ZERO).toString());
    }

    private void write(HttpServletResponse response, String sessionToken, String csrfToken) {
        Duration maxAge = sessionMaxAge();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(sessionToken, maxAge).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(csrfToken, maxAge).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCookie().getName(), value)
                .httpOnly(true)
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie csrfCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCookie().getCsrfName(), value)
                .httpOnly(false)
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private Duration sessionMaxAge() {
        return properties.getSession().getAbsoluteTimeout();
    }
}
