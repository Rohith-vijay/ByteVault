package com.bytevault.identity.repository;

import com.bytevault.identity.entity.VendorProfile;
import com.bytevault.identity.entity.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorProfileRepository extends JpaRepository<VendorProfile, UUID> {
    Optional<VendorProfile> findByUserId(UUID userId);
    Optional<VendorProfile> findByStoreSlug(String storeSlug);
    List<VendorProfile> findByStatus(VendorStatus status);
    boolean existsByStoreNameIgnoreCase(String storeName);
    boolean existsByStoreSlug(String storeSlug);
}
