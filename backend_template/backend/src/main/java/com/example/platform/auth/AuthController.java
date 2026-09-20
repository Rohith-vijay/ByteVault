package com.example.platform.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final com.example.platform.email.EmailService emailService;
    private final com.example.platform.security.JwtService jwtService;
    private final com.example.platform.auth.RefreshTokenService refreshTokenService;

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
    public void verifyEmail(@RequestParam("token") String token, HttpServletResponse response) throws java.io.IOException {
        try {
            emailVerificationService.verifyToken(token);
            log.info("Email verified successfully.");
            response.sendRedirect(getRedirectBaseUrl() + "/login?verified=true");
        } catch (Exception e) {
            log.error("Email verification failed.", e);
            response.sendRedirect(getRedirectBaseUrl() + "/login?error=invalid");
        }
    }

    @GetMapping("/login/success")
    public void oauthSuccess(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.core.user.OAuth2User oauth2User,
            HttpServletResponse response) throws java.io.IOException {
        if (oauth2User == null) {
            response.sendRedirect(getRedirectBaseUrl() + "/login?error=oauth_failed");
            return;
        }

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        if (email == null || email.trim().isEmpty()) {
            response.sendRedirect(getRedirectBaseUrl() + "/login?error=oauth_failed");
            return;
        }

        try {
            com.example.platform.user.User user = authService.oauthProvision(email, name);

            String accessToken = jwtService.generateToken(user);
            com.example.platform.auth.RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

            setCookie(response, accessToken);
            setRefreshCookie(response, refreshToken.getToken());

            String encodedToken = java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8);
            String encodedName  = java.net.URLEncoder.encode(user.getFullName() != null ? user.getFullName() : "", java.nio.charset.StandardCharsets.UTF_8);
            String encodedEmail = java.net.URLEncoder.encode(user.getEmail() != null ? user.getEmail() : "", java.nio.charset.StandardCharsets.UTF_8);
            String encodedRole  = java.net.URLEncoder.encode(user.getRole().name(), java.nio.charset.StandardCharsets.UTF_8);

            String redirectUrl = getRedirectBaseUrl()
                    + "/login?oauth_success=true"
                    + "&token=" + encodedToken
                    + "&name=" + encodedName
                    + "&email=" + encodedEmail
                    + "&role=" + encodedRole;

            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("Error during Google OAuth authentication redirect processing", e);
            response.sendRedirect(getRedirectBaseUrl() + "/login?error=oauth_failed");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<com.example.platform.common.api.ApiSuccessResponse<AuthenticationResponse.UserDto>> getMe(
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new com.example.platform.exception.UnauthorizedException("Not authenticated");
        }
        com.example.platform.user.User user = (com.example.platform.user.User) authentication.getPrincipal();
        AuthenticationResponse.UserDto userDto = AuthenticationResponse.UserDto.builder()
                .name(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
        return ResponseEntity.ok(
                com.example.platform.common.api.ApiSuccessResponse.<AuthenticationResponse.UserDto>builder()
                        .timestamp(java.time.LocalDateTime.now())
                        .status(200)
                        .message("Session retrieved successfully")
                        .data(userDto)
                        .build()
        );
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
            throw new com.example.platform.exception.BadRequestException("Missing refresh token");
        }

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(token);
        AuthenticationResponse auth = authService.refreshToken(refreshRequest);
        if (auth.getToken() != null) setCookie(response, auth.getToken());
        if (auth.getRefreshToken() != null) setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
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
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
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
