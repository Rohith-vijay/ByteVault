package com.bytevault.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginRequest {
    @NotBlank(message = "Provider is required")
    private String provider;

    @NotBlank(message = "OAuth ID is required")
    private String oauthId;

    private String email;
    private String fullName;
}
