package com.marketplace.auth.entity;

import com.marketplace.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_token",  columnList = "token"),
                @Index(name = "idx_refresh_user",   columnList = "credentials_id"),
                @Index(name = "idx_refresh_family", columnList = "family_id")
        })
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class RefreshToken extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credentials_id", nullable = false)
    private AuthCredentials credentials;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;
}
