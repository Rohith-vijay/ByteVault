package com.bytevault.warehouse.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.warehouse.entity.WarehouseLocation;
import com.bytevault.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/locations/{productId}")
    public ResponseEntity<ApiResponse<WarehouseLocation>> getLocation(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success("Warehouse location retrieved", warehouseService.getLocation(productId)));
    }

    @PostMapping("/locations")
    public ResponseEntity<ApiResponse<WarehouseLocation>> assignLocation(
            @RequestParam UUID productId,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String shelf,
            @RequestParam(required = false) String bin) {
        return ResponseEntity.ok(ApiResponse.success("Warehouse location assigned",
                warehouseService.assignLocation(productId, warehouseCode, shelf, bin)));
    }
}
