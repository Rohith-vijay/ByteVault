package com.bytevault.support;

import com.bytevault.support.dto.CreateMessageRequest;
import com.bytevault.support.dto.CreateTicketRequest;
import com.bytevault.support.dto.TicketMessageResponse;
import com.bytevault.support.dto.TicketResponse;
import com.bytevault.support.entity.*;
import com.bytevault.support.repository.TicketHistoryRepository;
import com.bytevault.support.repository.TicketMessageRepository;
import com.bytevault.support.repository.TicketRepository;
import com.bytevault.support.service.SupportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SupportServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMessageRepository ticketMessageRepository;

    @Mock
    private TicketHistoryRepository ticketHistoryRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private SupportService supportService;

    private UUID userId;
    private UUID ticketId;
    private Ticket mockTicket;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        mockTicket = Ticket.builder()
                .id(ticketId)
                .userId(userId)
                .userEmail("customer@bytevault.com")
                .subject("Defective item delivered")
                .description("Item arrived broken")
                .category("ORDER_ISSUE")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.HIGH)
                .build();
        mockTicket.setCreatedAt(LocalDateTime.now());
        mockTicket.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Create Ticket - Successfully persists ticket and initial message")
    void testCreateTicket_Success() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setSubject("Defective item delivered");
        request.setDescription("Item arrived broken");
        request.setCategory("ORDER_ISSUE");
        request.setPriority(TicketPriority.HIGH);

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMessageRepository.save(any(TicketMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = supportService.createTicket(userId, "customer@bytevault.com", request);

        assertNotNull(response);
        assertEquals(ticketId, response.getId());
        assertEquals("Defective item delivered", response.getSubject());
        assertEquals(TicketStatus.OPEN, response.getStatus());
        assertEquals(1, response.getMessages().size());

        verify(ticketRepository, times(1)).save(any(Ticket.class));
        verify(ticketMessageRepository, times(1)).save(any(TicketMessage.class));
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("Get Ticket - Owner can retrieve ticket successfully")
    void testGetTicketById_OwnerSuccess() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());

        TicketResponse response = supportService.getTicketById(ticketId, userId, false);

        assertNotNull(response);
        assertEquals(ticketId, response.getId());
    }

    @Test
    @DisplayName("Get Ticket - Non-owner customer cannot view ticket (IDOR Protected)")
    void testGetTicketById_IdorViolation_ThrowsAccessDenied() {
        UUID maliciousUserId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));

        assertThrows(AccessDeniedException.class, () ->
                supportService.getTicketById(ticketId, maliciousUserId, false)
        );
    }

    @Test
    @DisplayName("Get Ticket - Admin can view any user's ticket")
    void testGetTicketById_AdminAccess_Success() {
        UUID adminId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());

        TicketResponse response = supportService.getTicketById(ticketId, adminId, true);

        assertNotNull(response);
        assertEquals(ticketId, response.getId());
    }

    @Test
    @DisplayName("Add Message - Customer can reply to own ticket")
    void testAddMessage_CustomerReply_Success() {
        CreateMessageRequest request = new CreateMessageRequest();
        request.setMessage("Here is the tracking screenshot.");

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketMessageRepository.save(any(TicketMessage.class))).thenAnswer(inv -> {
            TicketMessage msg = inv.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        TicketMessageResponse response = supportService.addMessage(ticketId, userId, "customer@bytevault.com", "CUSTOMER", request, false);

        assertNotNull(response);
        assertEquals("Here is the tracking screenshot.", response.getMessage());
        assertEquals("CUSTOMER", response.getSenderRole());
    }

    @Test
    @DisplayName("Add Message - Cross-user reply is blocked (IDOR Protected)")
    void testAddMessage_IdorViolation_ThrowsAccessDenied() {
        UUID attackerId = UUID.randomUUID();
        CreateMessageRequest request = new CreateMessageRequest();
        request.setMessage("Unauthorized message injection");

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));

        assertThrows(AccessDeniedException.class, () ->
                supportService.addMessage(ticketId, attackerId, "attacker@bytevault.com", "CUSTOMER", request, false)
        );
    }
}
