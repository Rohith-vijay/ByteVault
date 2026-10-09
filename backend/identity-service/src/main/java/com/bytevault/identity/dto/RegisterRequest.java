package com.bytevault.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String fullName;
    private String firstName;
    private String lastName;
    private String role; // "CUSTOMER" or "VENDOR"

    // Vendor registration onboarding fields:
    private String storeName;
    private String storeDescription;
    private String businessTaxId;
    private String payoutInfo;
    private String supportEmail;
}
