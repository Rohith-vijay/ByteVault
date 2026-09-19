package com.bytevault.support.dto;

import com.bytevault.support.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Category is required")
    private String category; // ORDER, PAYMENT, DOWNLOAD, VENDOR, GENERAL

    @Builder.Default
    private TicketPriority priority = TicketPriority.MEDIUM;
}
