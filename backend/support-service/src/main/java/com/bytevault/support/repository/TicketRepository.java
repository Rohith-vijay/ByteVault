package com.bytevault.support.repository;

import com.bytevault.support.entity.Ticket;
import com.bytevault.support.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);
    List<Ticket> findAllByOrderByCreatedAtDesc();
}
