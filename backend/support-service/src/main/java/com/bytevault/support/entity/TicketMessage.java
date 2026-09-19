package com.bytevault.support.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ticket_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMessage extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(name = "sender_role", nullable = false)
    private String senderRole; // "CUSTOMER", "VENDOR", "ADMIN"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;
}
