package com.bytevault.support.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.support.dto.*;
import com.bytevault.support.entity.TicketStatus;
import com.bytevault.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/tickets")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {

        UUID userId = resolveUserId(headerUserId, authentication);
        String userEmail = headerUserEmail != null ? headerUserEmail : (authentication != null ? authentication.getName() : "user@bytevault.com");

        TicketResponse response = supportService.createTicket(userId, userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Support ticket created successfully", response));
    }

    @GetMapping({"/tickets/my", "/tickets/my-tickets"})
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getMyTickets(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        UUID userId = resolveUserId(headerUserId, authentication);
        List<TicketResponse> tickets = supportService.getUserTickets(userId);
        return ResponseEntity.ok(ApiResponse.success("Support tickets retrieved", tickets));
    }

    @GetMapping("/tickets/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        UUID requesterId = resolveUserId(headerUserId, authentication);
        boolean isAdmin = isAdminUser(authentication);

        TicketResponse ticket = supportService.getTicketById(id, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Ticket details retrieved", ticket));
    }

    @PostMapping("/tickets/{id}/messages")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> addMessage(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @Valid @RequestBody CreateMessageRequest request,
            Authentication authentication) {

        UUID senderId = resolveUserId(headerUserId, authentication);
        String senderEmail = headerUserEmail != null ? headerUserEmail : (authentication != null ? authentication.getName() : "user@bytevault.com");
        boolean isAdmin = isAdminUser(authentication);
        String senderRole = isAdmin ? "ADMIN" : (isVendorUser(authentication) ? "VENDOR" : "CUSTOMER");

        TicketMessageResponse message = supportService.addMessage(id, senderId, senderEmail, senderRole, request, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully", message));
    }

    @GetMapping("/tickets/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getAllTicketsAdmin(
            @RequestParam(required = false) TicketStatus status) {

        List<TicketResponse> tickets = supportService.getAllTicketsAdmin(status);
        return ResponseEntity.ok(ApiResponse.success("All support tickets retrieved for admin", tickets));
    }

    @RequestMapping(value = "/tickets/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicketStatus(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @RequestBody UpdateTicketStatusRequest request,
            Authentication authentication) {

        UUID adminId = resolveUserId(headerUserId, authentication);
        String adminEmail = headerUserEmail != null ? headerUserEmail : "admin@bytevault.com";

        TicketResponse updated = supportService.updateTicketStatus(id, adminId, adminEmail, request);
        return ResponseEntity.ok(ApiResponse.success("Ticket status updated successfully", updated));
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

    private boolean isAdminUser(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isVendorUser(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));
    }
}
