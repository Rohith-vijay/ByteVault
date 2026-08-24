package com.bytevault.fulfillment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "download_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "entitlement_id")
    private UUID entitlementId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String ipAddress;
    private String userAgent;

    @Column(nullable = false)
    private String status; // SUCCESS, DENIED, EXPIRED
}
