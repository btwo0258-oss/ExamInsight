package com.example.llm.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.api.AuthDtos;
import com.example.llm.auth.domain.AuthModels;
import com.example.llm.auth.security.AuthCookieManager;
import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.auth.service.AuthApplicationService;
import com.example.llm.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthApplicationService authService;
    private final AuthCookieManager cookieManager;
    private final AuthCrypto crypto;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(
            AuthApplicationService authService,
            AuthCookieManager cookieManager,
            AuthCrypto crypto,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.cookieManager = cookieManager;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!request.getRequestURI().startsWith(request.getContextPath() + "/api/v2/")) {
            writeError(response, new AuthApiException(
                    org.springframework.http.HttpStatus.GONE,
                    "LEGACY_API_DISABLED",
                    "旧版接口已停用，请升级到 V2 客户端。"));
            return false;
        }

        try {
            AuthModels.AuthenticatedSession session = authService.authenticate(
                    cookieManager.readSessionToken(request));
            if (session.rotatedSessionToken() != null) {
                cookieManager.writeRotated(
                        response, session.rotatedSessionToken(), session.rotatedCsrfToken());
            }

            if (requiresCsrf(request)
                    && !crypto.matches("csrf-token", request.getHeader("X-CSRF-Token"),
                    session.csrfSecretHash())) {
                throw new AuthApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "CSRF_VALIDATION_FAILED", "请求校验已失效，请刷新页面后重试。");
            }

            UserContext.setSession(session);
            return true;
        } catch (AuthApiException exception) {
            writeError(response, exception);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.remove();
    }

    private boolean requiresCsrf(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod())
                && !HttpMethod.HEAD.matches(request.getMethod())
                && !HttpMethod.OPTIONS.matches(request.getMethod());
    }

    private void writeError(HttpServletResponse response, AuthApiException exception) throws Exception {
        response.setStatus(exception.status().value());
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        AuthDtos.ErrorBody body = new AuthDtos.ErrorBody(
                exception.code(), exception.getMessage(), crypto.newExternalId(), exception.details());
        objectMapper.writeValue(response.getWriter(), new AuthDtos.ErrorEnvelope(body));
    }
}
