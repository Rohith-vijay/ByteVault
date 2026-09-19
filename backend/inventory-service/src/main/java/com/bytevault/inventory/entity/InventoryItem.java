package com.bytevault.inventory.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "is_digital", nullable = false)
    private Boolean isDigital;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    public int getAvailableQuantity() {
        if (Boolean.TRUE.equals(isDigital)) {
            return isAvailable ? 999999 : 0;
        }
        return Math.max(0, quantity - reservedQuantity);
    }
}
