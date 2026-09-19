package com.bytevault.inventory.integration;

import com.bytevault.inventory.entity.InventoryItem;
import com.bytevault.inventory.entity.InventoryReservation;
import com.bytevault.inventory.repository.InventoryRepository;
import com.bytevault.inventory.repository.InventoryReservationRepository;
import com.bytevault.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InventoryConcurrencyAndAtomicLockTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("Concurrent reservation test: 20 concurrent threads requesting stock when available=5 ensures <= 5 succeed without overselling")
    void testConcurrentInventoryReservation() throws InterruptedException {
        UUID productId = UUID.randomUUID();
        int initialStock = 5;
        int totalThreads = 20;

        InventoryItem item = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(initialStock)
                .reservedQuantity(0)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(item));

        // Emulate atomic DB conditional update (WHERE (quantity - reserved_quantity) >= 1)
        AtomicInteger remainingStock = new AtomicInteger(initialStock);
        when(inventoryRepository.atomicReserveStock(eq(productId), eq(1))).thenAnswer(inv -> {
            while (true) {
                int current = remainingStock.get();
                if (current <= 0) {
                    return 0; // DB update returns 0 rows updated
                }
                if (remainingStock.compareAndSet(current, current - 1)) {
                    return 1; // 1 row updated
                }
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger failedReservations = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean reserved = inventoryService.reserveStock(UUID.randomUUID(), productId, 1);
                    if (reserved) {
                        successfulReservations.incrementAndGet();
                    } else {
                        failedReservations.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedReservations.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(5, successfulReservations.get(), "Exactly 5 reservations must succeed");
        assertEquals(15, failedReservations.get(), "Remaining 15 reservations must be rejected safely");
        assertEquals(0, remainingStock.get(), "Remaining stock cannot become negative");
    }

    @Test
    @DisplayName("Concurrent duplicate reservations on same (orderId, productId) are handled safely")
    void testConcurrentDuplicateReservationRace() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        int totalThreads = 10;

        InventoryItem item = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(10)
                .reservedQuantity(0)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(item));

        AtomicInteger dbReserveCalls = new AtomicInteger(0);
        when(inventoryRepository.atomicReserveStock(eq(productId), eq(1))).thenAnswer(inv -> {
            dbReserveCalls.incrementAndGet();
            return 1;
        });

        // First thread succeeds in saving, subsequent throw DataIntegrityViolationException
        AtomicInteger savedCount = new AtomicInteger(0);
        when(inventoryReservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> {
            if (savedCount.incrementAndGet() > 1) {
                throw new DataIntegrityViolationException("Unique constraint violation: duplicate order reservation");
            }
            return inv.getArgument(0);
        });

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        AtomicInteger successfulResults = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean reserved = inventoryService.reserveStock(orderId, productId, 1);
                    if (reserved) {
                        successfulResults.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(totalThreads, successfulResults.get(), "All parallel retries must resolve to success idempotently");
    }

    @Test
    @DisplayName("Stock release restores reserved units atomically")
    void testStockRelease() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(10)
                .reservedQuantity(5)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(item));

        inventoryService.releaseStock(productId, 2);

        verify(inventoryRepository, times(1)).atomicReleaseStock(productId, 2);
    }
}
