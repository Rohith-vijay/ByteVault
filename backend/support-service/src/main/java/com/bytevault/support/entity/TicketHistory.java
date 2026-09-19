package com.bytevault.support.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ticket_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketHistory extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Column(nullable = false)
    private String action; // e.g., "STATUS_CHANGED", "PRIORITY_CHANGED", "ASSIGNED"

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;
}
