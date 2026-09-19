package com.bytevault.user.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String email;

    private String firstName;
    private String lastName;
    private String phone;

    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
