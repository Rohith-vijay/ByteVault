package com.bytevault.shipping.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "tracking_number", unique = true, nullable = false)
    private String trackingNumber;

    private String carrier;

    @Column(nullable = false, length = 30)
    private String status; // PENDING, MANIFESTED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, RETURNED, CANCELLED

    @Column(name = "shipping_address", length = 1000)
    private String shippingAddress;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;

    @Column(name = "weight_kg")
    private Double weightKg;

    private String notes;
}
