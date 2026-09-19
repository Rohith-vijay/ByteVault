package com.bytevault.user.entity;

public enum VendorStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /**
     * Validates if state transition from current status to next status is permitted.
     * 
     * Permitted transitions:
     * - PENDING_APPROVAL -> APPROVED, REJECTED
     * - APPROVED -> SUSPENDED
     * - SUSPENDED -> APPROVED (Reactivation)
     * - REJECTED -> PENDING_APPROVAL (Re-application)
     * - Current == Next -> true (Idempotent no-op)
     */
    public boolean canTransitionTo(VendorStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case PENDING_APPROVAL -> (next == APPROVED || next == REJECTED);
            case APPROVED -> (next == SUSPENDED);
            case SUSPENDED -> (next == APPROVED);
            case REJECTED -> (next == PENDING_APPROVAL);
        };
    }
}
