package com.bytevault.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private UUID userId;
    private String email;
    private String fullName;
    private String firstName;
    private String lastName;
    private String phone;
    private String avatarUrl;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
