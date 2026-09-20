package com.example.platform.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    Page<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail, Pageable pageable);
    
    long countByRecipientEmailAndIsReadFalse(String recipientEmail);
    
    List<Notification> findByRecipientEmailAndIsReadFalse(String recipientEmail);

    @Query("SELECT n FROM Notification n WHERE " +
            "((n.recipientEmail = :recipientEmail) OR (n.recipientRole = :recipientRole)) " +
            "AND (:category IS NULL OR n.category = :category) " +
            "AND (:isRead IS NULL OR n.isRead = :isRead) " +
            "AND (:search IS NULL OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Notification> searchNotifications(
            @Param("recipientEmail") String recipientEmail,
            @Param("recipientRole") String recipientRole,
            @Param("category") String category,
            @Param("isRead") Boolean isRead,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE " +
            "((n.recipientEmail = :recipientEmail) OR (n.recipientRole = :recipientRole)) " +
            "AND n.isRead = false")
    long countUnreadNotifications(
            @Param("recipientEmail") String recipientEmail,
            @Param("recipientRole") String recipientRole);

    @Query("SELECT n FROM Notification n WHERE " +
            "((n.recipientEmail = :recipientEmail) OR (n.recipientRole = :recipientRole)) " +
            "AND n.isRead = false")
    List<Notification> findUnreadNotifications(
            @Param("recipientEmail") String recipientEmail,
            @Param("recipientRole") String recipientRole);
}
