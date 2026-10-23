package com.bytevault.auth.controller;

import com.bytevault.auth.dto.AuthenticationRequest;
import com.bytevault.auth.dto.AuthenticationResponse;
import com.bytevault.auth.dto.ForgotPasswordRequest;
import com.bytevault.auth.dto.OAuthLoginRequest;
import com.bytevault.auth.dto.RefreshTokenRequest;
import com.bytevault.auth.dto.RegisterRequest;
import com.bytevault.auth.dto.ResetPasswordRequest;
import com.bytevault.auth.service.AuthService;
import com.bytevault.auth.service.EmailVerificationService;
import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.exception.BadRequestException;
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

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.jwt.expiration:900000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

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
        AuthenticationResponse auth = authService.register(request);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletResponse response) {
        AuthenticationResponse auth = authService.login(request);
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
        AuthenticationResponse auth = authService.refreshToken(refreshRequest);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If an account associated with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully. Please log in with your new credentials."));
    }

    @PostMapping("/oauth")
    public ResponseEntity<AuthenticationResponse> oauthLogin(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletResponse response) {
        AuthenticationResponse auth = authService.oauthLogin(request);
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
            authService.logout(token);
        }
        clearCookie(response);
        clearRefreshCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    private void setCookie(HttpServletResponse response, String token) {
        String cookie = String.format(
            "access_token=%s; Max-Age=%d; Path=/api; HttpOnly; %s SameSite=Lax",
            token,
            (int)(accessTokenExpirationMs / 1000),
            secureCookie ? "Secure;" : "");
        response.addHeader("Set-Cookie", cookie);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        String cookie = String.format(
            "refresh_token=%s; Max-Age=%d; Path=/api; HttpOnly; %s SameSite=Lax",
            token,
            (int)(refreshExpirationMs / 1000),
            secureCookie ? "Secure;" : "");
        response.addHeader("Set-Cookie", cookie);
    }

    private void clearCookie(HttpServletResponse response) {
        String cookie = String.format(
            "access_token=; Max-Age=0; Path=/api; HttpOnly; %s SameSite=Lax",
            secureCookie ? "Secure;" : "");
        response.addHeader("Set-Cookie", cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        String cookie = String.format(
            "refresh_token=; Max-Age=0; Path=/api; HttpOnly; %s SameSite=Lax",
            secureCookie ? "Secure;" : "");
        response.addHeader("Set-Cookie", cookie);
    }
}
