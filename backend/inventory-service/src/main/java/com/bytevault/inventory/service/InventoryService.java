package com.bytevault.inventory.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.inventory.dto.*;
import com.bytevault.inventory.entity.InventoryItem;
import com.bytevault.inventory.entity.InventoryReservation;
import com.bytevault.inventory.repository.InventoryRepository;
import com.bytevault.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    @Transactional(readOnly = true)
    public InventoryItem getInventory(UUID productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    log.info("[InventoryService] Initializing default digital stock policy for productId: {}", productId);
                    InventoryItem item = InventoryItem.builder()
                            .productId(productId)
                            .isDigital(true)
                            .isAvailable(true)
                            .quantity(999999)
                            .reservedQuantity(0)
                            .build();
                    return inventoryRepository.save(item);
                });
    }

    @Transactional(readOnly = true)
    public InventoryResponseDto getInventoryDto(UUID productId) {
        InventoryItem item = getInventory(productId);
        return mapToDto(item);
    }

    @Transactional
    public boolean reserveStock(UUID productId, int quantity) {
        UUID tempOrderId = UUID.randomUUID();
        return reserveStock(tempOrderId, productId, quantity);
    }

    @Transactional
    public boolean reserveStock(UUID orderId, UUID productId, int quantity) {
        if (quantity <= 0) {
            log.warn("[InventoryService] Invalid reservation quantity: {} for productId={}", quantity, productId);
            return false;
        }

        InventoryItem item = getInventory(productId);
        if (!Boolean.TRUE.equals(item.getIsAvailable())) {
            log.warn("[InventoryService] Product {} is currently unavailable for purchase.", productId);
            return false;
        }

        if (Boolean.TRUE.equals(item.getIsDigital())) {
            log.debug("[InventoryService] Product {} is digital. Virtual stock available.", productId);
            return true;
        }

        // Idempotency check: if reservation already exists for this orderId and productId
        if (orderId != null) {
            Optional<InventoryReservation> existing = inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId);
            if (existing.isPresent()) {
                String status = existing.get().getStatus();
                if ("RESERVED".equalsIgnoreCase(status) || "COMMITTED".equalsIgnoreCase(status)) {
                    log.info("[InventoryService] Idempotent replay: stock already reserved/committed for orderId={}, productId={}", orderId, productId);
                    return true;
                }
                if ("RELEASED".equalsIgnoreCase(status)) {
                    log.warn("[InventoryService] Cannot reserve on already RELEASED reservation for orderId={}, productId={}", orderId, productId);
                    return false;
                }
            }
        }

        int updated = inventoryRepository.atomicReserveStock(productId, quantity);
        if (updated > 0) {
            log.info("[InventoryService] Atomic DB lock reserved {} units for productId={}, orderId={}", quantity, productId, orderId);
            if (orderId != null) {
                try {
                    InventoryReservation reservation = InventoryReservation.builder()
                            .orderId(orderId)
                            .productId(productId)
                            .quantity(quantity)
                            .status("RESERVED")
                            .build();
                    inventoryReservationRepository.save(reservation);
                } catch (DataIntegrityViolationException e) {
                    log.warn("[InventoryService] Concurrent duplicate reservation detected on orderId={}, productId={}. Recovering idempotently.", orderId, productId);
                    return true;
                }
            }
            return true;
        } else {
            log.warn("[InventoryService] Insufficient physical stock or unavailable for productId={}, requested={}", productId, quantity);
            return false;
        }
    }

    @Transactional
    public StockReservationResponse reserveStockForOrder(StockReservationRequest request) {
        UUID orderId = request.getOrderId();
        List<StockReservationItemDto> items = request.getItems();

        if (items == null || items.isEmpty()) {
            return StockReservationResponse.builder()
                    .orderId(orderId)
                    .reserved(true)
                    .message("No items to reserve")
                    .itemResults(List.of())
                    .build();
        }

        List<StockReservationResponse.ItemReservationResult> results = new ArrayList<>();
        List<StockReservationItemDto> successfulReservations = new ArrayList<>();
        boolean allSuccess = true;

        for (StockReservationItemDto itemDto : items) {
            boolean reserved = reserveStock(orderId, itemDto.getProductId(), itemDto.getQuantity());
            results.add(StockReservationResponse.ItemReservationResult.builder()
                    .productId(itemDto.getProductId())
                    .requestedQuantity(itemDto.getQuantity())
                    .reserved(reserved)
                    .note(reserved ? "Reserved" : "Insufficient stock")
                    .build());

            if (reserved) {
                successfulReservations.add(itemDto);
            } else {
                allSuccess = false;
                break;
            }
        }

        if (!allSuccess) {
            // Rollback already reserved items for this orderId to prevent orphan lockups
            log.warn("[InventoryService] Batch reservation failed for orderId={}. Rolling back partial reservations...", orderId);
            for (StockReservationItemDto rollbackItem : successfulReservations) {
                releaseStock(orderId, rollbackItem.getProductId(), rollbackItem.getQuantity());
            }
            return StockReservationResponse.builder()
                    .orderId(orderId)
                    .reserved(false)
                    .message("Insufficient stock for one or more physical items")
                    .itemResults(results)
                    .build();
        }

        return StockReservationResponse.builder()
                .orderId(orderId)
                .reserved(true)
                .message("All items reserved successfully")
                .itemResults(results)
                .build();
    }

    @Transactional
    public void releaseStock(UUID productId, int quantity) {
        releaseStock(null, productId, quantity);
    }

    @Transactional
    public void releaseStock(UUID orderId, UUID productId, int quantity) {
        if (orderId != null) {
            Optional<InventoryReservation> optRes = inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId);
            if (optRes.isPresent()) {
                InventoryReservation res = optRes.get();
                if ("RELEASED".equalsIgnoreCase(res.getStatus())) {
                    log.info("[InventoryService] Idempotent skip: reservation already released for orderId={}, productId={}", orderId, productId);
                    return;
                }
                if ("COMMITTED".equalsIgnoreCase(res.getStatus())) {
                    log.warn("[InventoryService] Cannot release already COMMITTED stock for orderId={}, productId={}", orderId, productId);
                    return;
                }
                res.setStatus("RELEASED");
                inventoryReservationRepository.save(res);
            }
        }

        InventoryItem item = getInventory(productId);
        if (Boolean.TRUE.equals(item.getIsDigital())) {
            return;
        }

        inventoryRepository.atomicReleaseStock(productId, quantity);
        log.info("[InventoryService] Released {} units for physical productId={}, orderId={}", quantity, productId, orderId);
    }

    @Transactional
    public void releaseStockForOrder(UUID orderId) {
        log.info("[InventoryService] Releasing all reserved stock for orderId={}", orderId);
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderIdAndStatus(orderId, "RESERVED");
        if (reservations.isEmpty()) {
            log.info("[InventoryService] No active RESERVED reservations found to release for orderId={}", orderId);
            return;
        }

        for (InventoryReservation res : reservations) {
            InventoryItem item = getInventory(res.getProductId());
            if (!Boolean.TRUE.equals(item.getIsDigital())) {
                inventoryRepository.atomicReleaseStock(res.getProductId(), res.getQuantity());
            }
            res.setStatus("RELEASED");
            inventoryReservationRepository.save(res);
            log.info("[InventoryService] Released {} units for productId={} (orderId={})", res.getQuantity(), res.getProductId(), orderId);
        }
    }

    @Transactional
    public void commitStockForOrder(UUID orderId) {
        log.info("[InventoryService] Committing reserved stock for orderId={}", orderId);
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderIdAndStatus(orderId, "RESERVED");
        if (reservations.isEmpty()) {
            log.info("[InventoryService] No active RESERVED reservations found to commit for orderId={}", orderId);
            return;
        }

        for (InventoryReservation res : reservations) {
            InventoryItem item = getInventory(res.getProductId());
            if (!Boolean.TRUE.equals(item.getIsDigital())) {
                inventoryRepository.atomicCommitStock(res.getProductId(), res.getQuantity());
            }
            res.setStatus("COMMITTED");
            inventoryReservationRepository.save(res);
            log.info("[InventoryService] Committed {} units for productId={} (orderId={})", res.getQuantity(), res.getProductId(), orderId);
        }
    }

    @Transactional
    public InventoryResponseDto adjustStock(StockAdjustmentRequest request) {
        UUID productId = request.getProductId();
        if (productId == null) {
            throw new BadRequestException("Product ID is required for stock adjustment");
        }

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> InventoryItem.builder()
                        .productId(productId)
                        .isDigital(Boolean.TRUE.equals(request.getIsDigital()))
                        .isAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true)
                        .quantity(0)
                        .reservedQuantity(0)
                        .build());

        if (request.getQuantityAdjustment() != null) {
            int newQty = Math.max(0, item.getQuantity() + request.getQuantityAdjustment());
            item.setQuantity(newQty);
        } else if (request.getQuantity() != null) {
            item.setQuantity(Math.max(0, request.getQuantity()));
        }

        if (request.getIsDigital() != null) {
            item.setIsDigital(request.getIsDigital());
        }
        if (request.getIsAvailable() != null) {
            item.setIsAvailable(request.getIsAvailable());
        }

        InventoryItem saved = inventoryRepository.save(item);
        log.info("[InventoryService] Adjusted stock for productId={}: qty={}, reserved={}, isDigital={}, isAvailable={}",
                productId, saved.getQuantity(), saved.getReservedQuantity(), saved.getIsDigital(), saved.getIsAvailable());

        return mapToDto(saved);
    }

    private InventoryResponseDto mapToDto(InventoryItem item) {
        return InventoryResponseDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .isDigital(item.getIsDigital())
                .isAvailable(item.getIsAvailable())
                .quantity(item.getQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .availableQuantity(item.getAvailableQuantity())
                .build();
    }
}
