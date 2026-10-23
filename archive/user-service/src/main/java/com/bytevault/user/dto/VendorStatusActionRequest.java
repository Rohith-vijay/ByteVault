package com.bytevault.user.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorStatusActionRequest {
    private String reason;
}
