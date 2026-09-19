package com.bytevault.support.dto;

import com.bytevault.support.entity.TicketPriority;
import com.bytevault.support.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketStatusRequest {
    private TicketStatus status;
    private TicketPriority priority;
    private UUID assignedAdminId;
    private String adminNote;
}
