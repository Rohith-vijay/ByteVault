package com.example.platform.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByOwnerTypeAndOwnerIdOrderByOrderIndexAsc(String ownerType, Long ownerId);
    void deleteByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}
