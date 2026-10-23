package com.bytevault.identity.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.identity.dto.*;
import com.bytevault.identity.service.EmailVerificationService;
import com.bytevault.identity.service.IdentityService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IdentityService identityService;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private String getRedirectBaseUrl() {
        if (frontendUrl != null && !frontendUrl.trim().isEmpty() && !"*".equals(frontendUrl.trim())) {
            String[] urls = frontendUrl.split(",");
            return urls[0].trim();
        }
        return "http://localhost:5173";
    }

    @GetMapping("/verify")
    public void verifyEmail(@RequestParam("token") String token, HttpServletResponse response) throws IOException {
        try {
            emailVerificationService.verifyToken(token);
            log.info("Email verified successfully.");
            response.sendRedirect(getRedirectBaseUrl() + "/login?verified=true");
        } catch (Exception e) {
            log.error("Email verification failed.", e);
            response.sendRedirect(getRedirectBaseUrl() + "/login?error=invalid");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthenticationResponse auth = identityService.register(request);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletResponse response) {
        AuthenticationResponse auth = identityService.login(request);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refresh_token", required = false) String refreshTokenFromCookie,
            HttpServletResponse response) {
        
        String token = (request != null && request.getRefreshToken() != null) 
                ? request.getRefreshToken() 
                : refreshTokenFromCookie;

        if (token == null || token.trim().isEmpty()) {
            throw new BadRequestException("Missing refresh token");
        }

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(token);
        AuthenticationResponse auth = identityService.refreshToken(refreshRequest);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        identityService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If an account associated with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        identityService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully. Please log in with your new credentials."));
    }

    @org.springframework.beans.factory.annotation.Value("${app.oauth.enabled:false}")
    private boolean oauthEnabled;

    @PostMapping("/oauth")
    public ResponseEntity<AuthenticationResponse> oauthLogin(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletResponse response) {
        if (!oauthEnabled) {
            throw new com.bytevault.common.exception.BadRequestException(
                    "OAuth authentication is disabled by default: Unverified third-party ID tokens are rejected. Production IdP cryptographic verification required."
            );
        }
        AuthenticationResponse auth = identityService.oauthLogin(request);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refresh_token", required = false) String refreshTokenFromCookie,
            HttpServletResponse response) {
        
        String token = (request != null && request.getRefreshToken() != null) 
                ? request.getRefreshToken() 
                : refreshTokenFromCookie;

        if (token != null && !token.trim().isEmpty()) {
            identityService.logout(token);
        }
        clearCookie(response);
        clearRefreshCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    private void setCookie(HttpServletResponse response, String token) {
        String cookie = String.format(
            "access_token=%s; Max-Age=%d; Path=/api; HttpOnly; SameSite=Lax",
            token,
            28800
        );
        response.addHeader("Set-Cookie", cookie);
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        String cookie = String.format(
            "refresh_token=%s; Max-Age=%d; Path=/api/v1/auth/refresh; HttpOnly; SameSite=Strict",
            refreshToken,
            604800
        );
        response.addHeader("Set-Cookie", cookie);
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "access_token=; Max-Age=0; Path=/api; HttpOnly; SameSite=Lax");
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "refresh_token=; Max-Age=0; Path=/api/v1/auth/refresh; HttpOnly; SameSite=Strict");
    }
}
