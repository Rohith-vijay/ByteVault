package com.example.platform.captcha;

public interface CaptchaService {
    boolean verifyToken(String token, String remoteIp);
}
