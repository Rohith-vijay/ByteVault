package com.bytevault.warehouse.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "warehouse_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseLocation extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(length = 30)
    private String zone;

    @Column(length = 30)
    private String aisle;

    @Column(length = 30)
    private String shelf;

    @Column(length = 30)
    private String bin;

    private Integer capacity;

    @Column(name = "current_stock")
    private Integer currentStock;
}
