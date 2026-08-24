package com.marketplace.common.util;

import org.slf4j.MDC;
import java.util.UUID;

public class CorrelationIdUtil {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_LOG_VAR = "correlationId";

    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_LOG_VAR);
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(CORRELATION_ID_LOG_VAR, correlationId);
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID_LOG_VAR);
    }
}
