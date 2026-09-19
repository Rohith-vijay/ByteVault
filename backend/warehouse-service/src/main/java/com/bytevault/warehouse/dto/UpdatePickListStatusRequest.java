package com.bytevault.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePickListStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;
    private String notes;
}
