-- Product Service Schema Definition
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    vendor_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    sku VARCHAR(255) UNIQUE,
    product_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    category_id BIGINT REFERENCES categories(id),
    tags VARCHAR(500),
    file_storage_key VARCHAR(500),
    file_name VARCHAR(255),
    file_type VARCHAR(100),
    file_size BIGINT,
    file_version VARCHAR(50),
    file_checksum VARCHAR(255),
    physical_sku VARCHAR(255),
    physical_weight DOUBLE PRECISION,
    physical_dimensions VARCHAR(255),
    weight DOUBLE PRECISION,
    length DOUBLE PRECISION,
    width DOUBLE PRECISION,
    height DOUBLE PRECISION,
    shipping_class VARCHAR(100),
    moderation_reason TEXT,
    moderated_by UUID,
    moderated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_products_vendor_id ON products(vendor_id);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_product_type ON products(product_type);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
