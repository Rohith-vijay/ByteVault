package com.bytevault.warehouse.repository;

import com.bytevault.warehouse.entity.PickList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PickListRepository extends JpaRepository<PickList, UUID> {
    Optional<PickList> findByOrderId(UUID orderId);
    List<PickList> findByWarehouseCode(String warehouseCode);
    List<PickList> findByStatus(String status);
    List<PickList> findByAssignedStaffId(UUID assignedStaffId);
}
