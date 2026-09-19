package com.bytevault.warehouse.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.warehouse.dto.GeneratePickListRequest;
import com.bytevault.warehouse.dto.PickListItemDto;
import com.bytevault.warehouse.dto.PickListResponseDto;
import com.bytevault.warehouse.entity.PickList;
import com.bytevault.warehouse.entity.PickListItem;
import com.bytevault.warehouse.entity.WarehouseLocation;
import com.bytevault.warehouse.event.WarehouseEventPublisher;
import com.bytevault.warehouse.repository.PickListItemRepository;
import com.bytevault.warehouse.repository.PickListRepository;
import com.bytevault.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickListService {

    private final PickListRepository pickListRepository;
    private final PickListItemRepository pickListItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseEventPublisher warehouseEventPublisher;

    @Transactional
    public PickList generatePickList(GeneratePickListRequest request) {
        UUID orderId = request.getOrderId();
        Optional<PickList> existing = pickListRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("[PickListService] Idempotent replay: PickList already exists for orderId={}", orderId);
            return existing.get();
        }

        String warehouseCode = request.getWarehouseCode() != null ? request.getWarehouseCode() : "WH-MAIN";
        PickList pickList = PickList.builder()
                .orderId(orderId)
                .warehouseCode(warehouseCode)
                .status("PENDING")
                .assignedStaffId(request.getAssignedStaffId())
                .notes(request.getNotes())
                .build();

        PickList saved = pickListRepository.save(pickList);

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (PickListItemDto itemDto : request.getItems()) {
                WarehouseLocation location = warehouseRepository.findByProductId(itemDto.getProductId())
                        .orElse(null);

                PickListItem item = PickListItem.builder()
                        .pickListId(saved.getId())
                        .productId(itemDto.getProductId())
                        .sku(itemDto.getSku() != null ? itemDto.getSku() : "SKU-" + itemDto.getProductId().toString().substring(0, 8))
                        .productTitle(itemDto.getProductTitle() != null ? itemDto.getProductTitle() : "Product")
                        .quantity(itemDto.getQuantity() != null ? itemDto.getQuantity() : 1)
                        .warehouseCode(location != null ? location.getWarehouseCode() : warehouseCode)
                        .zone(location != null ? location.getZone() : "ZONE-A")
                        .aisle(location != null ? location.getAisle() : "A1")
                        .shelf(location != null ? location.getShelf() : "S-01")
                        .bin(location != null ? location.getBin() : "B-01")
                        .isPicked(false)
                        .build();

                pickListItemRepository.save(item);
            }
        }

        warehouseEventPublisher.publishPickListCreated(saved);
        log.info("[PickListService] Generated PickList for orderId={}, itemsCount={}", orderId,
                request.getItems() != null ? request.getItems().size() : 0);

        return saved;
    }

    @Transactional(readOnly = true)
    public PickList getPickListById(UUID id) {
        return pickListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PickList not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public PickList getPickListByOrderId(UUID orderId) {
        return pickListRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickList not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public PickListResponseDto getPickListDtoByOrderId(UUID orderId) {
        PickList pickList = getPickListByOrderId(orderId);
        List<PickListItem> items = pickListItemRepository.findByPickListId(pickList.getId());
        return mapToDto(pickList, items);
    }

    @Transactional(readOnly = true)
    public PickListResponseDto getPickListDtoById(UUID id) {
        PickList pickList = getPickListById(id);
        List<PickListItem> items = pickListItemRepository.findByPickListId(pickList.getId());
        return mapToDto(pickList, items);
    }

    @Transactional
    public PickList updatePickListStatus(UUID id, String newStatus, String notes) {
        PickList pickList = getPickListById(id);
        String current = pickList.getStatus();

        if (current.equalsIgnoreCase(newStatus)) {
            return pickList;
        }

        if (!isValidStatusTransition(current, newStatus)) {
            throw new BadRequestException("Invalid pick list status transition from " + current + " to " + newStatus);
        }

        pickList.setStatus(newStatus.toUpperCase());
        if (notes != null) pickList.setNotes(notes);
        PickList updated = pickListRepository.save(pickList);

        if ("PACKED".equalsIgnoreCase(newStatus)) {
            warehouseEventPublisher.publishPickListPacked(updated);
        }

        log.info("[PickListService] Updated PickList status id={}, newStatus={}", id, newStatus);
        return updated;
    }

    @Transactional
    public PickListItem markItemPicked(UUID pickListId, UUID itemId, boolean isPicked) {
        PickListItem item = pickListItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PickListItem not found with id: " + itemId));

        if (!item.getPickListId().equals(pickListId)) {
            throw new BadRequestException("Item does not belong to pick list: " + pickListId);
        }

        item.setIsPicked(isPicked);
        PickListItem updated = pickListItemRepository.save(item);

        // Check if all items are picked
        List<PickListItem> items = pickListItemRepository.findByPickListId(pickListId);
        boolean allPicked = items.stream().allMatch(PickListItem::getIsPicked);
        if (allPicked) {
            PickList pickList = getPickListById(pickListId);
            if ("PENDING".equalsIgnoreCase(pickList.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(pickList.getStatus())) {
                pickList.setStatus("PICKED");
                pickListRepository.save(pickList);
                log.info("[PickListService] All items picked! PickList id={} auto-advanced to PICKED", pickListId);
            }
        }

        return updated;
    }

    private boolean isValidStatusTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equalsIgnoreCase(to)) return true;

        String f = from.toUpperCase();
        String t = to.toUpperCase();

        return switch (f) {
            case "PENDING" -> t.equals("IN_PROGRESS") || t.equals("PICKED") || t.equals("CANCELLED");
            case "IN_PROGRESS" -> t.equals("PICKED") || t.equals("CANCELLED");
            case "PICKED" -> t.equals("PACKED") || t.equals("CANCELLED");
            case "PACKED", "CANCELLED" -> false;
            default -> false;
        };
    }

    private PickListResponseDto mapToDto(PickList pickList, List<PickListItem> items) {
        return PickListResponseDto.builder()
                .id(pickList.getId())
                .orderId(pickList.getOrderId())
                .warehouseCode(pickList.getWarehouseCode())
                .status(pickList.getStatus())
                .assignedStaffId(pickList.getAssignedStaffId())
                .notes(pickList.getNotes())
                .createdAt(pickList.getCreatedAt())
                .items(items.stream().map(this::mapItemToDto).collect(Collectors.toList()))
                .build();
    }

    private PickListItemDto mapItemToDto(PickListItem item) {
        return PickListItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .sku(item.getSku())
                .productTitle(item.getProductTitle())
                .quantity(item.getQuantity())
                .warehouseCode(item.getWarehouseCode())
                .zone(item.getZone())
                .aisle(item.getAisle())
                .shelf(item.getShelf())
                .bin(item.getBin())
                .isPicked(item.getIsPicked())
                .build();
    }
}
