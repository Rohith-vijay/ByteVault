package com.bytevault.order.service;

import com.bytevault.order.entity.Order;
import com.bytevault.order.entity.OrderStatus;
import com.bytevault.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled background job to expire stale PENDING orders.
 * Prevents orders from indefinitely rotting in PENDING if checkout is abandoned or payment times out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final OrderRepository orderRepository;

    @Scheduled(fixedDelayString = "${app.order.expiry-check-interval-ms:60000}")
    @Transactional
    public void expireStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<Order> staleOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
        if (staleOrders.isEmpty()) {
            return;
        }

        log.info("[OrderExpiryService] Found {} stale pending order(s) older than 30 minutes. Expiring to CANCELLED...", staleOrders.size());
        for (Order order : staleOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("[OrderExpiryService] Stale order {} automatically expired to CANCELLED", order.getId());
        }
    }
}
