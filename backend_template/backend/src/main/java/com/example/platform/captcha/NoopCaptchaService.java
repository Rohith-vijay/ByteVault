package com.example.platform.captcha;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopCaptchaService implements CaptchaService {
    @Override
    public boolean verifyToken(String token, String remoteIp) {
        log.info("[NoopCaptchaService] Auto-approving CAPTCHA validation (NOOP).");
        return true;
    }
}
