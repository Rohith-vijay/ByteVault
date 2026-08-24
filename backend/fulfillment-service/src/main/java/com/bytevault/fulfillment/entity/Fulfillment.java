package com.bytevault.fulfillment.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fulfillments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fulfillment extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status; // IN_PROGRESS, FULFILLED, FAILED

    private LocalDateTime fulfilledAt;
}
