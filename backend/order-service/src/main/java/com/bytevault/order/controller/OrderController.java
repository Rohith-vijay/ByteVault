package com.bytevault.order.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.order.dto.CreateOrderRequest;
import com.bytevault.order.dto.OrderResponse;
import com.bytevault.order.dto.OrderPaymentConfirmationRequest;
import com.bytevault.order.entity.OrderStatus;
import com.bytevault.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {

        UUID userId = resolveUserId(headerUserId, authentication);
        String customerEmail = headerUserEmail != null ? headerUserEmail : request.getCustomerEmail();
        String customerName = request.getCustomerName() != null ? request.getCustomerName() : "Customer";

        OrderResponse orderResponse = orderService.createOrder(userId, customerEmail, customerName, request);
        return ResponseEntity.ok(ApiResponse.success("Order created successfully", orderResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        UUID userId = resolveUserId(headerUserId, authentication);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        OrderResponse orderResponse = orderService.getOrderById(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", orderResponse));
    }

    @GetMapping({"", "/my-orders", "/my"})
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getUserOrders(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        UUID userId = resolveUserId(headerUserId, authentication);
        List<OrderResponse> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(ApiResponse.success("User orders retrieved successfully", orders));
    }

    @GetMapping("/vendor/my-sales")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<com.bytevault.order.dto.VendorSaleResponse>>> getVendorSales(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        
        UUID vendorId = resolveUserId(headerUserId, authentication);
        List<com.bytevault.order.dto.VendorSaleResponse> sales = orderService.getVendorSales(vendorId);
        return ResponseEntity.ok(ApiResponse.success("Vendor sales retrieved successfully", sales));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrdersAdmin() {
        List<OrderResponse> orders = orderService.getAllOrdersForAdmin();
        return ResponseEntity.ok(ApiResponse.success("All orders retrieved for admin", orders));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID id,
            @RequestParam OrderStatus status) {

        log.info("[OrderController] Status update request by admin: orderId={}, status={}", id, status);
        OrderResponse updated = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", updated));
    }

    @PostMapping("/internal/{id}/mark-paid")
    public ResponseEntity<ApiResponse<OrderResponse>> markOrderPaidInternal(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            @Valid @RequestBody OrderPaymentConfirmationRequest request,
            Authentication authentication) {

        log.info("[OrderController] Internal mark-paid request received for orderId={}", id);

        if (!isInternalAuthorized(gatewaySecretHeader, userRolesHeader, authentication)) {
            log.warn("[OrderController] Unauthorized attempt to call internal mark-paid for orderId={}", id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access Forbidden: Internal service authentication required.", 403));
        }

        OrderResponse response = orderService.markOrderPaid(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order marked as PAID and outbox event created", response));
    }

    /**
     * Internal endpoint: returns per-vendor gross subtotals for a given order.
     * Called by Payment Service immediately after mark-paid succeeds, to write VendorLedger rows.
     * Secured by X-Gateway-Secret + ROLE_INTERNAL_SERVICE (same as mark-paid).
     */
    @GetMapping("/internal/{id}/vendor-breakdown")
    public ResponseEntity<ApiResponse<List<com.bytevault.common.dto.VendorBreakdownEntry>>> getVendorBreakdown(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            Authentication authentication) {

        if (!isInternalAuthorized(gatewaySecretHeader, userRolesHeader, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access Forbidden: Internal service authentication required.", 403));
        }

        List<com.bytevault.common.dto.VendorBreakdownEntry> breakdown = orderService.getVendorBreakdown(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor breakdown retrieved", breakdown));
    }

    /**
     * Internal endpoint: applies a refund-driven CANCELLED transition and triggers entitlement revocation.
     * Called by Payment Service's RefundService on full refund.
     * Secured identically to mark-paid; NOT the same as the admin updateOrderStatus endpoint.
     */
    @PostMapping("/internal/{id}/refund-cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> refundCancelInternal(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            Authentication authentication) {

        if (!isInternalAuthorized(gatewaySecretHeader, userRolesHeader, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access Forbidden: Internal service authentication required.", 403));
        }

        log.info("[OrderController] Internal refund-cancel for orderId={}", id);
        OrderResponse response = orderService.applyRefundCancel(id);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled via refund path", response));
    }

    /**
     * Internal endpoint: moves order to FAILED on payment failure.
     * Secured identically to mark-paid (X-Gateway-Secret + internal role).
     */
    @PostMapping("/internal/{id}/mark-failed")
    public ResponseEntity<ApiResponse<OrderResponse>> markFailedInternal(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            @RequestParam(value = "reason", required = false, defaultValue = "Payment failed") String reason,
            Authentication authentication) {

        if (!isInternalAuthorized(gatewaySecretHeader, userRolesHeader, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access Forbidden: Internal service authentication required.", 403));
        }

        log.info("[OrderController] Internal mark-failed for orderId={}, reason={}", id, reason);
        OrderResponse response = orderService.markOrderFailed(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Order marked as FAILED", response));
    }



    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody(required = false) java.util.Map<String, String> payload,
            Authentication authentication) {

        UUID userId = resolveUserId(headerUserId, authentication);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String reason = payload != null ? payload.get("reason") : "Cancelled by user";

        log.info("[OrderController] Cancel order request: orderId={}, userId={}, isAdmin={}", id, userId, isAdmin);
        OrderResponse response = orderService.cancelOrder(id, userId, isAdmin, reason);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }

    private UUID resolveUserId(String headerUserId, Authentication authentication) {
        if (headerUserId != null && !headerUserId.trim().isEmpty()) {
            try {
                return UUID.fromString(headerUserId);
            } catch (Exception ignored) {}
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (Exception ignored) {
                return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
            }
        }
        throw new IllegalArgumentException("User identity could not be resolved from authentication context");
    }

    private boolean isInternalAuthorized(String gatewaySecretHeader, String userRolesHeader, Authentication auth) {
        boolean hasInternalOrAdmin =
                (userRolesHeader != null && (userRolesHeader.contains("ROLE_INTERNAL_SERVICE") || userRolesHeader.contains("ROLE_ADMIN")))
                || (auth != null && auth.getAuthorities().stream().anyMatch(
                        a -> a.getAuthority().equals("ROLE_INTERNAL_SERVICE") || a.getAuthority().equals("ROLE_ADMIN")));
        boolean validSecret = gatewaySecretHeader != null && gatewaySecretHeader.equals(gatewaySecret);
        return validSecret && hasInternalOrAdmin;
    }
}
