package com.example.llm.auth.controller;

import com.example.llm.auth.api.AccountDtos;
import com.example.llm.auth.domain.AuthModels;
import com.example.llm.auth.security.AuthCookieManager;
import com.example.llm.auth.security.ClientRequestMetadataFactory;
import com.example.llm.auth.service.AccountApplicationService;
import com.example.llm.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/account")
public class AccountController {
    private final AccountApplicationService accountService;
    private final ClientRequestMetadataFactory metadataFactory;
    private final AuthCookieManager cookieManager;

    public AccountController(
            AccountApplicationService accountService,
            ClientRequestMetadataFactory metadataFactory,
            AuthCookieManager cookieManager) {
        this.accountService = accountService;
        this.metadataFactory = metadataFactory;
        this.cookieManager = cookieManager;
    }

    @PatchMapping("/profile")
    public AccountDtos.AccountResponse updateProfile(
            @Valid @RequestBody AccountDtos.UpdateProfileRequest request,
            HttpServletRequest servletRequest) {
        return accountService.updateProfile(
                UserContext.requireSession(), request, metadataFactory.from(servletRequest));
    }

    @PostMapping("/deletion-requests")
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody AccountDtos.DeleteAccountRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthModels.AuthenticatedSession session = UserContext.requireSession();
        accountService.deleteAccount(session, request, metadataFactory.from(servletRequest));
        cookieManager.clear(servletResponse);
        return ResponseEntity.noContent().build();
    }
}
