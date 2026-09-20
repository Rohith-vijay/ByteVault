-- 1. Create Users Table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

-- 2. Create Refresh Tokens Table
CREATE TABLE refresh_tokens (
    id BINARY(16) NOT NULL PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Create Verification Tokens Table
CREATE TABLE verification_tokens (
    id BINARY(16) NOT NULL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 4. Create Audit Logs Table
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(255),
    performed_by VARCHAR(255),
    target_resource VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(255),
    user_agent VARCHAR(500),
    status VARCHAR(255),
    error_message VARCHAR(2000),
    timestamp TIMESTAMP(6)
);

-- 5. Create Notifications Table
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_email VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    category VARCHAR(255) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    correlation_id VARCHAR(100),
    recipient_user_id BIGINT,
    recipient_role VARCHAR(50),
    notification_type VARCHAR(100),
    target_entity VARCHAR(100),
    target_entity_id BIGINT,
    created_by VARCHAR(255),
    read_at TIMESTAMP(6),
    priority VARCHAR(20) DEFAULT 'MEDIUM'
);
