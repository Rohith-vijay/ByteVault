package com.marketplace.auth.captcha;

public interface CaptchaService {
    boolean verifyToken(String token, String remoteIp);
}
