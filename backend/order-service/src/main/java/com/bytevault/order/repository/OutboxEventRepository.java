package com.bytevault.order.repository;

import com.bytevault.order.entity.OutboxEvent;
import com.bytevault.order.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) ORDER BY o.createdAt ASC")
    List<OutboxEvent> findReadyToPublish(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now, Pageable pageable);

    Optional<OutboxEvent> findByAggregateIdAndEventType(UUID aggregateId, String eventType);

    List<OutboxEvent> findByStatus(OutboxStatus status);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :processingStatus WHERE o.id = :id AND o.status = :pendingStatus")
    int claimEventForProcessing(@Param("id") UUID id,
                                @Param("pendingStatus") OutboxStatus pendingStatus,
                                @Param("processingStatus") OutboxStatus processingStatus);
}
