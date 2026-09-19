package com.bytevault.product.repository;

import com.bytevault.product.entity.Product;
import com.bytevault.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByVendorId(UUID vendorId);
    List<Product> findByVendorIdAndStatus(UUID vendorId, ProductStatus status);
    Optional<Product> findByIdAndVendorId(UUID id, UUID vendorId);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
}
