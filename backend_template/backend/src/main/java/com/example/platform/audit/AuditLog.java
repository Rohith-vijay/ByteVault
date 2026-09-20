package com.example.platform.audit;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;      // e.g., "USER_LOGIN", "UPDATE_SETTINGS"
    private String performedBy; // Email or User ID
    private String targetResource;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    private String ipAddress;
    
    @Column(length = 500)
    private String userAgent;
    
    private String status;      // "SUCCESS", "FAILED"
    
    @Column(length = 2000)
    private String errorMessage;
    
    private LocalDateTime timestamp;
}
