package com.bytevault.inventory.integration;

import com.bytevault.inventory.entity.InventoryItem;
import com.bytevault.inventory.repository.InventoryRepository;
import com.bytevault.inventory.repository.InventoryReservationRepository;
import com.bytevault.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory_concurrency_test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false"
})
public class InventoryDatabaseConcurrencyLockTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        inventoryReservationRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Database-backed concurrency test: 20 parallel threads execute atomic conditional update against JPA repository with stock=5")
    void testRealDatabaseAtomicConditionalUpdateConcurrency() throws InterruptedException {
        UUID productId = UUID.randomUUID();
        int initialStock = 5;
        int totalThreads = 20;

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            InventoryItem item = InventoryItem.builder()
                    .productId(productId)
                    .isDigital(false)
                    .isAvailable(true)
                    .quantity(initialStock)
                    .reservedQuantity(0)
                    .build();
            return inventoryRepository.saveAndFlush(item);
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

        // Trigger simultaneous execution across threads
        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(5, successfulReservations.get(), "Exactly 5 atomic database updates must succeed");
        assertEquals(15, failedReservations.get(), "Remaining 15 atomic updates must return 0 rows updated");

        InventoryItem finalState = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(5, finalState.getReservedQuantity(), "Reserved quantity in database must be exactly 5");
        assertEquals(0, finalState.getAvailableQuantity(), "Available quantity must be exactly 0 (no overselling)");
    }

    @Test
    @DisplayName("Database-backed test: atomic release restores stock in database")
    void testRealDatabaseAtomicRelease() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            InventoryItem item = InventoryItem.builder()
                    .productId(productId)
                    .isDigital(false)
                    .isAvailable(true)
                    .quantity(10)
                    .reservedQuantity(0)
                    .build();
            inventoryRepository.saveAndFlush(item);
            return null;
        });

        // Reserve 5
        boolean reserved = inventoryService.reserveStock(orderId, productId, 5);
        assertTrue(reserved);

        // Release 2
        inventoryService.releaseStock(orderId, productId, 2);

        InventoryItem updated = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(3, updated.getReservedQuantity());
        assertEquals(7, updated.getAvailableQuantity());
    }

    @Test
    @DisplayName("Database-backed test: unavailable stock (isAvailable=false) rejects atomic reservation")
    void testRealDatabaseUnavailableStockRejected() {
        UUID productId = UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            InventoryItem item = InventoryItem.builder()
                    .productId(productId)
                    .isDigital(false)
                    .isAvailable(false) // Deactivated product
                    .quantity(50)
                    .reservedQuantity(0)
                    .build();
            return inventoryRepository.saveAndFlush(item);
        });

        boolean reserved = inventoryService.reserveStock(UUID.randomUUID(), productId, 1);
        assertEquals(false, reserved, "Unavailable product must reject reservation");
    }
}
