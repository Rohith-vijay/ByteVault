package com.bytevault.product.entity;

public enum ProductStatus {
    DRAFT,
    PUBLISHED,
    DEACTIVATED,
    ARCHIVED;

    public boolean canTransitionTo(ProductStatus next) {
        if (next == null || this == next) return true;
        return switch (this) {
            case DRAFT -> next == PUBLISHED || next == DEACTIVATED || next == ARCHIVED;
            case PUBLISHED -> next == DEACTIVATED || next == ARCHIVED;
            case DEACTIVATED -> next == PUBLISHED || next == ARCHIVED;
            case ARCHIVED -> false; // Terminal state
        };
    }
}
