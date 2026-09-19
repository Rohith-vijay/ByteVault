package com.bytevault.user.service;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.user.dto.UpdateProfileRequest;
import com.bytevault.user.dto.UserProfileResponse;
import com.bytevault.user.entity.UserProfile;
import com.bytevault.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final com.bytevault.user.repository.UserAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public java.util.List<com.bytevault.user.entity.UserAddress> getUserAddresses(UUID userId) {
        return addressRepository.findByUserId(userId);
    }

    @Transactional
    public com.bytevault.user.entity.UserAddress addAddress(UUID userId, com.bytevault.user.entity.UserAddress address) {
        address.setUserId(userId);
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        addressRepository.findByIdAndUserId(addressId, userId).ifPresent(addressRepository::delete);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("[UserProfileService] Initializing default profile for user: {}", userId);
                    UserProfile newProfile = UserProfile.builder()
                            .userId(userId)
                            .email("user-" + userId + "@bytevault.internal")
                            .build();
                    return profileRepository.save(newProfile);
                });
        return mapToResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.builder()
                        .userId(userId)
                        .email("user-" + userId + "@bytevault.internal")
                        .build());

        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getState() != null) profile.setState(request.getState());
        if (request.getPostalCode() != null) profile.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) profile.setCountry(request.getCountry());

        UserProfile saved = profileRepository.save(profile);
        log.info("[UserProfileService] Updated profile for userId={}", userId);
        return mapToResponse(saved);
    }

    @Transactional
    public void createProfileFromEvent(UUID userId, String email, String fullName) {
        if (profileRepository.findByUserId(userId).isPresent()) {
            log.info("[UserProfileService] Profile already exists for userId={}", userId);
            return;
        }

        String firstName = null;
        String lastName = null;
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            firstName = parts[0];
            if (parts.length > 1) {
                lastName = parts[1];
            }
        }

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        profileRepository.save(profile);
        log.info("[UserProfileService] Created user profile from registration event: userId={}, email={}", userId, email);
    }

    private UserProfileResponse mapToResponse(UserProfile p) {
        return UserProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .email(p.getEmail())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phone(p.getPhone())
                .address(p.getAddress())
                .city(p.getCity())
                .state(p.getState())
                .postalCode(p.getPostalCode())
                .country(p.getCountry())
                .build();
    }
}
