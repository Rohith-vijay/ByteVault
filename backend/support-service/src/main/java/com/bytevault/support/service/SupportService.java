package com.bytevault.support.service;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.support.dto.*;
import com.bytevault.support.entity.*;
import com.bytevault.support.repository.TicketHistoryRepository;
import com.bytevault.support.repository.TicketMessageRepository;
import com.bytevault.support.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public TicketResponse createTicket(UUID userId, String userEmail, CreateTicketRequest request) {
        Ticket ticket = Ticket.builder()
                .userId(userId)
                .userEmail(userEmail)
                .subject(request.getSubject())
                .description(request.getDescription())
                .category(request.getCategory())
                .status(TicketStatus.OPEN)
                .priority(request.getPriority() != null ? request.getPriority() : TicketPriority.MEDIUM)
                .build();

        ticket = ticketRepository.save(ticket);
        log.info("[SupportService] Created new ticket: ticketId={}, userId={}, subject={}", ticket.getId(), userId, ticket.getSubject());

        // Create initial message
        TicketMessage initialMsg = TicketMessage.builder()
                .ticket(ticket)
                .senderId(userId)
                .senderEmail(userEmail)
                .senderRole("CUSTOMER")
                .message(request.getDescription())
                .build();
        ticketMessageRepository.save(initialMsg);

        // Record history
        TicketHistory history = TicketHistory.builder()
                .ticketId(ticket.getId())
                .actorId(userId)
                .actorEmail(userEmail)
                .action("TICKET_CREATED")
                .oldValue(null)
                .newValue(TicketStatus.OPEN.name())
                .build();
        ticketHistoryRepository.save(history);

        // Publish event
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("ticketId", ticket.getId().toString());
            event.put("userId", userId.toString());
            event.put("subject", ticket.getSubject());
            event.put("status", ticket.getStatus().name());
            rabbitTemplate.convertAndSend("support.exchange", "ticket.created", event);
        } catch (Exception e) {
            log.warn("[SupportService] RabbitMQ publish skipped for ticket.created: {}", e.getMessage());
        }

        return mapToResponse(ticket, List.of(initialMsg));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getUserTickets(UUID userId) {
        List<Ticket> tickets = ticketRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return tickets.stream()
                .map(t -> mapToResponse(t, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId, UUID requesterId, boolean isAdmin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        // IDOR Check: Non-admins can only view their own tickets
        if (!isAdmin && !ticket.getUserId().equals(requesterId)) {
            log.warn("[SupportService] IDOR violation attempt: requesterId={} attempted to access ticketId={} owned by userId={}", requesterId, ticketId, ticket.getUserId());
            throw new AccessDeniedException("Access Denied: You do not have permission to view this ticket.");
        }

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return mapToResponse(ticket, messages);
    }

    @Transactional
    public TicketMessageResponse addMessage(UUID ticketId, UUID senderId, String senderEmail, String senderRole, CreateMessageRequest request, boolean isAdmin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        // IDOR Check: Non-admins can only reply to their own tickets
        if (!isAdmin && !ticket.getUserId().equals(senderId)) {
            throw new AccessDeniedException("Access Denied: You cannot reply to another user's ticket.");
        }

        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .senderId(senderId)
                .senderEmail(senderEmail)
                .senderRole(senderRole)
                .message(request.getMessage())
                .build();
        message = ticketMessageRepository.save(message);

        // Update ticket status based on who replied
        if (isAdmin && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
        } else if (!isAdmin && ticket.getStatus() == TicketStatus.WAITING_FOR_CUSTOMER) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
        }

        log.info("[SupportService] Added message to ticket: ticketId={}, senderRole={}", ticketId, senderRole);
        return mapToMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getAllTicketsAdmin(TicketStatus statusFilter) {
        List<Ticket> tickets = (statusFilter != null)
                ? ticketRepository.findByStatusOrderByCreatedAtDesc(statusFilter)
                : ticketRepository.findAllByOrderByCreatedAtDesc();

        return tickets.stream()
                .map(t -> mapToResponse(t, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @Transactional
    public TicketResponse updateTicketStatus(UUID ticketId, UUID adminId, String adminEmail, UpdateTicketStatusRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));

        if (request.getStatus() != null && request.getStatus() != ticket.getStatus()) {
            TicketHistory history = TicketHistory.builder()
                    .ticketId(ticketId)
                    .actorId(adminId)
                    .actorEmail(adminEmail)
                    .action("STATUS_CHANGED")
                    .oldValue(ticket.getStatus().name())
                    .newValue(request.getStatus().name())
                    .build();
            ticketHistoryRepository.save(history);
            ticket.setStatus(request.getStatus());
        }

        if (request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            ticket.setPriority(request.getPriority());
        }

        if (request.getAssignedAdminId() != null) {
            ticket.setAssignedAdminId(request.getAssignedAdminId());
        }

        ticket = ticketRepository.save(ticket);
        log.info("[SupportService] Admin updated ticket: ticketId={}, newStatus={}", ticketId, ticket.getStatus());

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return mapToResponse(ticket, messages);
    }

    private TicketResponse mapToResponse(Ticket ticket, List<TicketMessage> messages) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .userId(ticket.getUserId())
                .userEmail(ticket.getUserEmail())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .assignedAdminId(ticket.getAssignedAdminId())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .messages(messages.stream().map(this::mapToMessageResponse).collect(Collectors.toList()))
                .build();
    }

    private TicketMessageResponse mapToMessageResponse(TicketMessage message) {
        return TicketMessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSenderId())
                .senderEmail(message.getSenderEmail())
                .senderRole(message.getSenderRole())
                .message(message.getMessage())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
