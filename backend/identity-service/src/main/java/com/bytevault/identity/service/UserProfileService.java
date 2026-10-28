package com.bytevault.identity.service;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.identity.dto.UpdateProfileRequest;
import com.bytevault.identity.dto.UserProfileResponse;
import com.bytevault.identity.entity.UserAddress;
import com.bytevault.identity.entity.UserProfile;
import com.bytevault.identity.repository.UserAddressRepository;
import com.bytevault.identity.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<UserAddress> getUserAddresses(UUID userId) {
        return addressRepository.findByUserProfileUserId(userId);
    }

    @Transactional
    public UserAddress addAddress(UUID userId, UserAddress address) {
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found for ID: " + userId));
        address.setUserProfile(profile);
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        addressRepository.findByIdAndUserProfileUserId(addressId, userId).ifPresent(addressRepository::delete);
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
        if (request.getFullName() != null) profile.setFullName(request.getFullName());
        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) profile.setAvatarUrl(request.getAvatarUrl());
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
    public UserProfile createProfile(UUID userId, String email, String fullName, String firstName, String lastName) {
        if (profileRepository.findByUserId(userId).isPresent()) {
            return profileRepository.findByUserId(userId).get();
        }

        String fName = firstName;
        String lName = lastName;
        if (fName == null && fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            fName = parts[0];
            if (parts.length > 1) {
                lName = parts[1];
            }
        }

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .email(email)
                .fullName(fullName != null ? fullName : (fName != null ? fName + (lName != null ? " " + lName : "") : null))
                .firstName(fName)
                .lastName(lName)
                .build();

        return profileRepository.save(profile);
    }

    public UserProfileResponse mapToResponse(UserProfile p) {
        return UserProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .email(p.getEmail())
                .fullName(p.getFullName())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phone(p.getPhone())
                .avatarUrl(p.getAvatarUrl())
                .address(p.getAddress())
                .city(p.getCity())
                .state(p.getState())
                .postalCode(p.getPostalCode())
                .country(p.getCountry())
                .build();
    }
}
