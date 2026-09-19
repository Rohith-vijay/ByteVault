package com.bytevault.warehouse.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "pick_lists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickList extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(nullable = false, length = 30)
    private String status; // PENDING, IN_PROGRESS, PICKED, PACKED, CANCELLED

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    private String notes;
}
