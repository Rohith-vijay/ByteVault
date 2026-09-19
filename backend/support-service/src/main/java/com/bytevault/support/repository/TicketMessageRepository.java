package com.bytevault.support.repository;

import com.bytevault.support.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {
    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
