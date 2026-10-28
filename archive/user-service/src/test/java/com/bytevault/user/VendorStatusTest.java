package com.bytevault.user;

import com.bytevault.user.entity.VendorStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VendorStatusTest {

    @Test
    @DisplayName("Valid transitions from PENDING_APPROVAL")
    void testPendingApprovalTransitions() {
        assertTrue(VendorStatus.PENDING_APPROVAL.canTransitionTo(VendorStatus.APPROVED));
        assertTrue(VendorStatus.PENDING_APPROVAL.canTransitionTo(VendorStatus.REJECTED));
        assertTrue(VendorStatus.PENDING_APPROVAL.canTransitionTo(VendorStatus.PENDING_APPROVAL)); // Idempotent
        assertFalse(VendorStatus.PENDING_APPROVAL.canTransitionTo(VendorStatus.SUSPENDED));
    }

    @Test
    @DisplayName("Valid transitions from APPROVED")
    void testApprovedTransitions() {
        assertTrue(VendorStatus.APPROVED.canTransitionTo(VendorStatus.SUSPENDED));
        assertTrue(VendorStatus.APPROVED.canTransitionTo(VendorStatus.APPROVED)); // Idempotent
        assertFalse(VendorStatus.APPROVED.canTransitionTo(VendorStatus.PENDING_APPROVAL));
        assertFalse(VendorStatus.APPROVED.canTransitionTo(VendorStatus.REJECTED));
    }

    @Test
    @DisplayName("Valid transitions from SUSPENDED")
    void testSuspendedTransitions() {
        assertTrue(VendorStatus.SUSPENDED.canTransitionTo(VendorStatus.APPROVED)); // Reactivate
        assertTrue(VendorStatus.SUSPENDED.canTransitionTo(VendorStatus.SUSPENDED)); // Idempotent
        assertFalse(VendorStatus.SUSPENDED.canTransitionTo(VendorStatus.PENDING_APPROVAL));
        assertFalse(VendorStatus.SUSPENDED.canTransitionTo(VendorStatus.REJECTED));
    }

    @Test
    @DisplayName("Valid transitions from REJECTED")
    void testRejectedTransitions() {
        assertTrue(VendorStatus.REJECTED.canTransitionTo(VendorStatus.PENDING_APPROVAL)); // Re-apply
        assertTrue(VendorStatus.REJECTED.canTransitionTo(VendorStatus.REJECTED)); // Idempotent
        assertFalse(VendorStatus.REJECTED.canTransitionTo(VendorStatus.APPROVED));
        assertFalse(VendorStatus.REJECTED.canTransitionTo(VendorStatus.SUSPENDED));
    }
}
