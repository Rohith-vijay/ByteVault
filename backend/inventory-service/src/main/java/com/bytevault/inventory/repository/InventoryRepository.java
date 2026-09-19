package com.bytevault.inventory.repository;

import com.bytevault.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByProductId(UUID productId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.reservedQuantity = i.reservedQuantity + :qty " +
           "WHERE i.productId = :productId AND (i.quantity - i.reservedQuantity) >= :qty AND i.isAvailable = true")
    int atomicReserveStock(@Param("productId") UUID productId, @Param("qty") int qty);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.reservedQuantity = CASE WHEN i.reservedQuantity >= :qty THEN i.reservedQuantity - :qty ELSE 0 END " +
           "WHERE i.productId = :productId")
    int atomicReleaseStock(@Param("productId") UUID productId, @Param("qty") int qty);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.quantity = CASE WHEN i.quantity >= :qty THEN i.quantity - :qty ELSE 0 END, " +
           "i.reservedQuantity = CASE WHEN i.reservedQuantity >= :qty THEN i.reservedQuantity - :qty ELSE 0 END " +
           "WHERE i.productId = :productId")
    int atomicCommitStock(@Param("productId") UUID productId, @Param("qty") int qty);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.quantity = i.quantity + :adjustment " +
           "WHERE i.productId = :productId")
    int atomicAdjustStock(@Param("productId") UUID productId, @Param("adjustment") int adjustment);
}
