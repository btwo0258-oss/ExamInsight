package com.example.llm.common;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.domain.AuthModels;
import org.springframework.http.HttpStatus;

public class UserContext {
    private static final ThreadLocal<AuthModels.AuthenticatedSession> SESSION = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setSession(AuthModels.AuthenticatedSession session) {
        SESSION.set(session);
    }

    public static Long getUserId() {
        AuthModels.AuthenticatedSession session = SESSION.get();
        return session == null ? null : session.userId();
    }

    public static AuthModels.AuthenticatedSession requireSession() {
        AuthModels.AuthenticatedSession session = SESSION.get();
        if (session == null) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED,
                    "SESSION_INVALID", "登录状态已失效，请重新登录。");
        }
        return session;
    }

    public static void remove() {
        SESSION.remove();
    }
}
