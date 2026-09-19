package com.bytevault.warehouse.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "pick_list_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickListItem extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pick_list_id", nullable = false)
    private UUID pickListId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    private String sku;

    @Column(name = "product_title")
    private String productTitle;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "warehouse_code")
    private String warehouseCode;

    private String zone;
    private String aisle;
    private String shelf;
    private String bin;

    @Column(name = "is_picked", nullable = false)
    @Builder.Default
    private Boolean isPicked = false;
}
