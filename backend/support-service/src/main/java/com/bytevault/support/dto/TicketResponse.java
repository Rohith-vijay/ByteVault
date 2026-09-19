package com.bytevault.support.dto;

import com.bytevault.support.entity.TicketPriority;
import com.bytevault.support.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String subject;
    private String description;
    private String category;
    private TicketStatus status;
    private TicketPriority priority;
    private UUID assignedAdminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<TicketMessageResponse> messages = new ArrayList<>();
}
