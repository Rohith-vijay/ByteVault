package com.marketplace.auth.entity;

import com.marketplace.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens",
        indexes = {
                @Index(name = "idx_verification_token", columnList = "token"),
                @Index(name = "idx_verification_credentials", columnList = "credentials_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credentials_id", nullable = false)
    private AuthCredentials credentials;

    @Column(nullable = false)
    private Instant expiryDate;
}
