package com.marketplace.notification.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateBuilder {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.mail.from.name:App System}")
    private String platformName;

    @Value("${app.mail.from.email:info@example.com}")
    private String platformEmail;

    public String getFrontendUrl() {
        if (frontendUrl != null && !frontendUrl.trim().isEmpty() && !"*".equals(frontendUrl.trim())) {
            String[] urls = frontendUrl.split(",");
            return urls[0].trim();
        }
        return "http://localhost:5173";
    }

    private String buildTemplate(String name, String contentHtml, String ctaText, String ctaUrl) {
        String base = getFrontendUrl();
        String ctaHtml = "";
        if (ctaText != null && ctaUrl != null) {
            ctaHtml = """
                    <div class="email-cta-container">
                      <a href="%s" class="email-cta-button">%s</a>
                    </div>
                    """.formatted(ctaUrl, ctaText);
        }
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    body {
                      font-family: 'Inter', Helvetica, Arial, sans-serif;
                      background-color: #f7f9fc;
                      margin: 0;
                      padding: 0;
                      color: #333333;
                    }
                    .email-container {
                      max-width: 600px;
                      margin: 20px auto;
                      background: #ffffff;
                      border-radius: 12px;
                      overflow: hidden;
                      box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
                      border: 1px solid #eef2f6;
                    }
                    .email-header {
                      background-color: #1a1a2e;
                      padding: 24px;
                      text-align: center;
                    }
                    .email-logo {
                      height: 60px;
                      width: 60px;
                      border-radius: 50%%;
                      display: block;
                      margin: 0 auto 10px auto;
                    }
                    .email-title {
                      color: #ffffff;
                      font-size: 20px;
                      font-weight: 700;
                      margin: 0;
                      letter-spacing: 0.5px;
                    }
                    .email-body {
                      padding: 30px 24px;
                      line-height: 1.6;
                      font-size: 15px;
                    }
                    .email-greeting {
                      font-size: 16px;
                      font-weight: 600;
                      color: #1a1a2e;
                      margin-top: 0;
                      margin-bottom: 16px;
                    }
                    .email-text {
                      color: #555555;
                      margin-bottom: 24px;
                    }
                    .email-cta-container {
                      text-align: center;
                      margin: 30px 0;
                    }
                    .email-cta-button {
                      background-color: #B07A3F;
                      color: #ffffff !important;
                      text-decoration: none;
                      padding: 12px 28px;
                      font-weight: 600;
                      border-radius: 8px;
                      display: inline-block;
                      box-shadow: 0 4px 10px rgba(176, 122, 63, 0.25);
                    }
                    .email-footer {
                      background-color: #f7f9fc;
                      padding: 24px;
                      text-align: center;
                      font-size: 12px;
                      color: #888888;
                      border-top: 1px solid #eef2f6;
                    }
                    .email-footer a {
                      color: #B07A3F;
                      text-decoration: none;
                    }
                    .email-divider {
                      border: 0;
                      border-top: 1px solid #eef2f6;
                      margin: 20px 0;
                    }
                  </style>
                </head>
                <body>
                  <div class="email-container">
                    <div class="email-header">
                      <div class="email-title">%s</div>
                    </div>
                    <div class="email-body">
                      <div class="email-greeting">Hello %s,</div>
                      <div class="email-text">
                        %s
                      </div>
                      %s
                    </div>
                    <div class="email-footer">
                      <p>This email was sent by %s.</p>
                      <p>
                        <a href="%s">Visit Website</a> | 
                        <a href="mailto:%s">Contact Us</a>
                      </p>
                      <p>&copy; 2026 %s. All rights reserved.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(platformName, platformName, name, contentHtml, ctaHtml, platformName, base, platformEmail, platformName);
    }

    public String buildWelcomeEmail(String name) {
        String base = getFrontendUrl();
        String content = """
                <p style="font-size:16px; color:#1a1a2e; font-weight:600; margin-bottom:6px;">Welcome aboard! 🙏</p>
                <p>We are thrilled to welcome you to the <strong>%s</strong> community.</p>
                <p>Your account is now active and ready. Here is what you can do:</p>
                <ul>
                  <li>Set up your profile</li>
                  <li>Explore the system dashboard</li>
                  <li>Configure your preferences</li>
                </ul>
                <p style="color:#888; font-size:13px;">If you have any questions, reply to this email or contact us at <a href="mailto:%s" style="color:#B07A3F;">%s</a>.</p>
                """.formatted(platformName, platformEmail, platformEmail);
        return buildTemplate(name, content, "Go to Dashboard", base + "/dashboard");
    }

    public String buildGoogleWelcomeEmail(String name) {
        String base = getFrontendUrl();
        String content = """
                <p style="font-size:16px; color:#1a1a2e; font-weight:600; margin-bottom:6px;">Welcome aboard! 🙏</p>
                <p>Thank you for joining <strong>%s</strong> with your Google account.</p>
                <p>You can sign in anytime using your Google account — no password needed.</p>
                <p style="color:#888; font-size:13px;">If you have any questions, reply to this email or contact us at <a href="mailto:%s" style="color:#B07A3F;">%s</a>.</p>
                """.formatted(platformName, platformEmail, platformEmail);
        return buildTemplate(name, content, "Go to Dashboard", base + "/dashboard");
    }

    public String buildVerificationEmail(String name, String verificationLink) {
        String content = """
                <p>Thanks for creating your account with <strong>%s</strong>.</p>
                <p>Please verify your email address by clicking the button below. This link will expire in <strong>24 hours</strong>.</p>
                <div style="background:#fff8f0; border:1px solid #f0dfc0; border-radius:8px; padding:14px 16px; margin:20px 0; font-size:13px; color:#888;">
                  <strong style="color:#1a1a2e;">Why verify?</strong><br>
                  Email verification ensures the security of your account and credentials.
                </div>
                <p style="color:#888; font-size:13px;">If you did not create this account, you can safely ignore this email — no action is needed.</p>
                """.formatted(platformName);
        return buildTemplate(name, content, "Verify My Email", verificationLink);
    }

    public String buildPasswordResetEmail(String name, String resetLink) {
        String content = """
                <p>We received a request to reset your password for your <strong>%s</strong> account.</p>
                <p>Click the button below to choose a new password. This link will expire in 30 minutes for security reasons.</p>
                <p>If you did not request this, you can safely ignore this email.</p>
                """.formatted(platformName);
        return buildTemplate(name, content, "Reset Password", resetLink);
    }

    public String buildPasswordChangedEmail(String name, String date) {
        String content = """
                <p>This is to confirm that the password for your <strong>%s</strong> account has been successfully changed.</p>
                <div style="background-color: #f7f9fc; border-left: 4px solid #B07A3F; padding: 12px; margin: 20px 0; border-radius: 0 8px 8px 0;">
                  <strong>Change Details:</strong><br>
                  Time: %s<br>
                  Status: Completed Successfully
                </div>
                <p><strong>Security Advice:</strong> If you did not authorize this change, reset your password immediately or contact our support team at <a href="mailto:%s">%s</a>.</p>
                """.formatted(platformName, date, platformEmail, platformEmail);
        return buildTemplate(name, content, "Secure Account", getFrontendUrl() + "/login");
    }
}
