package com.bytevault.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HtmlSanitizerTest {

    @Test
    @DisplayName("Sanitize dangerous script tags from user inputs")
    void testScriptSanitization() {
        String dangerousInput = "<script>alert('xss')</script>Hello World";
        String clean = HtmlSanitizer.sanitize(dangerousInput);

        assertNotNull(clean);
        assertFalse(clean.toLowerCase().contains("<script>"));
        assertFalse(clean.toLowerCase().contains("alert"));
    }

    @Test
    @DisplayName("Sanitize javascript onclick and onerror event handlers")
    void testEventHandlerSanitization() {
        String dangerousInput = "<img src='x' onerror='alert(1)'>";
        String clean = HtmlSanitizer.sanitize(dangerousInput);

        assertNotNull(clean);
        assertFalse(clean.toLowerCase().contains("onerror"));
    }
}
