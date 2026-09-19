package com.bytevault.warehouse.service;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.warehouse.dto.AssignLocationRequest;
import com.bytevault.warehouse.dto.WarehouseLocationResponseDto;
import com.bytevault.warehouse.entity.WarehouseLocation;
import com.bytevault.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public WarehouseLocation getLocation(UUID productId) {
        return warehouseRepository.findByProductId(productId)
                .orElseGet(() -> WarehouseLocation.builder()
                        .productId(productId)
                        .warehouseCode("WH-MAIN")
                        .zone("ZONE-A")
                        .aisle("A1")
                        .shelf("A1")
                        .bin("B-01")
                        .capacity(1000)
                        .currentStock(0)
                        .build());
    }

    @Transactional(readOnly = true)
    public WarehouseLocationResponseDto getLocationDto(UUID productId) {
        WarehouseLocation location = getLocation(productId);
        return mapToDto(location);
    }

    @Transactional
    public WarehouseLocation assignLocation(UUID productId, String warehouseCode, String shelf, String bin) {
        return assignLocation(AssignLocationRequest.builder()
                .productId(productId)
                .warehouseCode(warehouseCode)
                .shelf(shelf)
                .bin(bin)
                .zone("ZONE-A")
                .aisle("A1")
                .build());
    }

    @Transactional
    public WarehouseLocation assignLocation(AssignLocationRequest request) {
        UUID productId = request.getProductId();
        WarehouseLocation location = warehouseRepository.findByProductId(productId)
                .orElseGet(() -> WarehouseLocation.builder().productId(productId).build());

        location.setWarehouseCode(request.getWarehouseCode() != null ? request.getWarehouseCode() : "WH-MAIN");
        location.setZone(request.getZone() != null ? request.getZone() : "ZONE-A");
        location.setAisle(request.getAisle() != null ? request.getAisle() : "A1");
        location.setShelf(request.getShelf() != null ? request.getShelf() : "S-01");
        location.setBin(request.getBin() != null ? request.getBin() : "B-01");
        if (request.getCapacity() != null) location.setCapacity(request.getCapacity());
        if (request.getCurrentStock() != null) location.setCurrentStock(request.getCurrentStock());

        WarehouseLocation saved = warehouseRepository.save(location);
        log.info("[WarehouseService] Assigned location for productId={}: warehouse={}, zone={}, aisle={}, shelf={}, bin={}",
                productId, saved.getWarehouseCode(), saved.getZone(), saved.getAisle(), saved.getShelf(), saved.getBin());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WarehouseLocationResponseDto> getLocationsByWarehouse(String warehouseCode) {
        return warehouseRepository.findByWarehouseCode(warehouseCode).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private WarehouseLocationResponseDto mapToDto(WarehouseLocation loc) {
        return WarehouseLocationResponseDto.builder()
                .id(loc.getId())
                .productId(loc.getProductId())
                .warehouseCode(loc.getWarehouseCode())
                .zone(loc.getZone())
                .aisle(loc.getAisle())
                .shelf(loc.getShelf())
                .bin(loc.getBin())
                .capacity(loc.getCapacity())
                .currentStock(loc.getCurrentStock())
                .build();
    }
}
