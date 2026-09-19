package com.bytevault.warehouse.repository;

import com.bytevault.warehouse.entity.WarehouseLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<WarehouseLocation, UUID> {
    Optional<WarehouseLocation> findByProductId(UUID productId);
    List<WarehouseLocation> findByWarehouseCode(String warehouseCode);
    List<WarehouseLocation> findByWarehouseCodeAndZone(String warehouseCode, String zone);
}
