// Lightweight Frontend Observability/Logging Abstraction
const USE_REAL_MONITORING = false;
const MONITORING_ENDPOINT = "http://localhost:8080/api/v1/telemetry";

export const logger = {
  info: (message, context = {}) => {
    console.log(`%c[INFO] %c${new Date().toISOString()} - ${message}`, "color: #3B82F6; font-weight: bold;", "color: inherit;", context);
  },
  
  warn: (message, context = {}) => {
    console.warn(`%c[WARN] %c${new Date().toISOString()} - ${message}`, "color: #F59E0B; font-weight: bold;", "color: inherit;", context);
  },
  
  error: (message, error = null, context = {}) => {
    const errorDetails = {
      message: error?.message || String(error),
      stack: error?.stack,
      timestamp: new Date().toISOString(),
      url: window.location.href,
      userAgent: navigator.userAgent,
      ...context
    };

    console.error(`%c[ERROR] %c${errorDetails.timestamp} - ${message}`, "color: #EF4444; font-weight: bold;", "color: inherit;", errorDetails);

    if (USE_REAL_MONITORING) {
      fetch(MONITORING_ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ level: "ERROR", message, details: errorDetails })
      }).catch(err => console.warn("Failed to dispatch telemetry report", err));
    }
  }
};

export default logger;
