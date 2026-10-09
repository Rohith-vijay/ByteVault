package com.bytevault.identity.repository;

import com.bytevault.identity.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {
    List<UserAddress> findByUserProfileUserId(UUID userId);
    Optional<UserAddress> findByIdAndUserProfileUserId(UUID id, UUID userId);
}
