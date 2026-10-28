package com.bytevault.user.repository;

import com.bytevault.user.entity.VendorProfile;
import com.bytevault.user.entity.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorProfileRepository extends JpaRepository<VendorProfile, UUID> {

    Optional<VendorProfile> findByUserId(UUID userId);

    Optional<VendorProfile> findByStoreSlug(String storeSlug);

    List<VendorProfile> findByStatusOrderByCreatedAtDesc(VendorStatus status);

    List<VendorProfile> findAllByOrderByCreatedAtDesc();

    boolean existsByStoreSlug(String storeSlug);

    boolean existsByUserId(UUID userId);
}
