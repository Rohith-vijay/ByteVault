-- ByteVault Media: Vendor Profiles Schema Migration
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(30),
    country VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(30) NOT NULL,
    country VARCHAR(100) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS vendor_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    store_name VARCHAR(100) NOT NULL,
    store_slug VARCHAR(120) NOT NULL UNIQUE,
    store_description TEXT,
    logo_url VARCHAR(500),
    support_email VARCHAR(100),
    business_tax_id VARCHAR(50),
    payout_info TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    rejection_reason TEXT,
    suspension_reason TEXT,
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_vendor_profiles_user_id ON vendor_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_vendor_profiles_status ON vendor_profiles(status);
CREATE INDEX IF NOT EXISTS idx_vendor_profiles_store_slug ON vendor_profiles(store_slug);
