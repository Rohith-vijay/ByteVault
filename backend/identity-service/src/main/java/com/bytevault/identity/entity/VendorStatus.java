package com.bytevault.identity.entity;

public enum VendorStatus {
    PENDING_APPROVAL,
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isPending() {
        return this == PENDING_APPROVAL || this == PENDING;
    }

    public boolean canTransitionTo(VendorStatus next) {
        if (this == next) {
            return true;
        }
        if (this.isPending()) {
            return next == APPROVED || next == REJECTED;
        }
        return switch (this) {
            case APPROVED -> (next == SUSPENDED);
            case SUSPENDED -> (next == APPROVED);
            case REJECTED -> (next == PENDING_APPROVAL || next == PENDING);
            default -> false;
        };
    }
}
