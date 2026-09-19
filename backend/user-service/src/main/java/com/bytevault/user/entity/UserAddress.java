package com.bytevault.user.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String fullName;
    private String phone;
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    @Builder.Default
    @Column(name = "is_default")
    private Boolean isDefault = false;
}
