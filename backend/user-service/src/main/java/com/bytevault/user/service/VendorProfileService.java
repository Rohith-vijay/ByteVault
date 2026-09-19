package com.bytevault.user.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.user.dto.UpdateVendorProfileRequest;
import com.bytevault.user.dto.VendorProfileResponse;
import com.bytevault.user.dto.VendorStatusSummaryResponse;
import com.bytevault.user.entity.UserProfile;
import com.bytevault.user.entity.VendorProfile;
import com.bytevault.user.entity.VendorStatus;
import com.bytevault.user.repository.UserProfileRepository;
import com.bytevault.user.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorProfileService {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserProfileRepository userProfileRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public VendorProfile createPendingVendorProfile(
            UUID userId,
            String email,
            String storeName,
            String storeDescription,
            String businessTaxId,
            String payoutInfo,
            String supportEmail) {

        Optional<VendorProfile> existing = vendorProfileRepository.findByUserId(userId);
        if (existing.isPresent()) {
            log.info("[VendorProfileService] Vendor profile already exists for userId={}", userId);
            return existing.get();
        }

        String effectiveStoreName = (storeName != null && !storeName.trim().isEmpty())
                ? storeName.trim()
                : "Store-" + userId.toString().substring(0, 8);

        String baseSlug = toSlug(effectiveStoreName);
        String uniqueSlug = generateUniqueSlug(baseSlug);

        VendorProfile profile = VendorProfile.builder()
                .userId(userId)
                .storeName(effectiveStoreName)
                .storeSlug(uniqueSlug)
                .storeDescription(storeDescription)
                .supportEmail(supportEmail != null ? supportEmail.trim() : email)
                .businessTaxId(businessTaxId)
                .payoutInfo(payoutInfo)
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        VendorProfile saved = vendorProfileRepository.saveAndFlush(profile);
        log.info("[VendorProfileService] Created new vendor profile in PENDING_APPROVAL: id={}, userId={}, store={}",
                saved.getId(), userId, saved.getStoreName());
        return saved;
    }

    @Transactional
    public VendorProfileResponse getVendorProfileByUserId(UUID userId) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("[VendorProfileService] Auto-initializing pending profile for vendor userId={}", userId);
                    return createPendingVendorProfile(userId, null, null, null, null, null, null);
                });
        return mapToResponse(profile);
    }

    @Transactional
    public VendorProfileResponse updateVendorProfile(UUID userId, UpdateVendorProfileRequest request) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseGet(() -> createPendingVendorProfile(userId, null, null, null, null, null, null));

        if (request.getStoreName() != null && !request.getStoreName().trim().isEmpty()) {
            profile.setStoreName(request.getStoreName().trim());
        }
        if (request.getStoreDescription() != null) {
            profile.setStoreDescription(request.getStoreDescription());
        }
        if (request.getLogoUrl() != null) {
            profile.setLogoUrl(request.getLogoUrl());
        }
        if (request.getSupportEmail() != null) {
            profile.setSupportEmail(request.getSupportEmail());
        }
        if (request.getBusinessTaxId() != null) {
            profile.setBusinessTaxId(request.getBusinessTaxId());
        }
        if (request.getPayoutInfo() != null) {
            profile.setPayoutInfo(request.getPayoutInfo());
        }

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Updated vendor profile for userId={}", userId);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VendorProfileResponse> getPendingVendors() {
        return vendorProfileRepository.findByStatusOrderByCreatedAtDesc(VendorStatus.PENDING_APPROVAL)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VendorProfileResponse> getAllVendors(VendorStatus statusFilter) {
        List<VendorProfile> list;
        if (statusFilter != null) {
            list = vendorProfileRepository.findByStatusOrderByCreatedAtDesc(statusFilter);
        } else {
            list = vendorProfileRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VendorProfileResponse getVendorById(UUID id) {
        VendorProfile profile = findProfileByIdOrUserId(id);
        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public VendorProfileResponse getVendorBySlug(String slug) {
        VendorProfile profile = vendorProfileRepository.findByStoreSlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with slug: " + slug));
        return mapToResponse(profile);
    }

    @Transactional
    public VendorProfileResponse approveVendor(UUID id, UUID adminId) {
        VendorProfile profile = findProfileByIdOrUserId(id);
        VendorStatus current = profile.getStatus();

        if (current == VendorStatus.APPROVED) {
            log.info("[VendorProfileService] Vendor already approved: id={}", profile.getId());
            return mapToResponse(profile);
        }

        if (!current.canTransitionTo(VendorStatus.APPROVED)) {
            throw new BadRequestException(String.format(
                    "Invalid state transition: Cannot approve vendor currently in '%s' status.", current));
        }

        profile.setStatus(VendorStatus.APPROVED);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setRejectionReason(null);
        profile.setSuspensionReason(null);

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor APPROVED by admin {}: vendorId={}, store={}",
                adminId, saved.getId(), saved.getStoreName());
        return mapToResponse(saved);
    }

    @Transactional
    public VendorProfileResponse rejectVendor(UUID id, UUID adminId, String reason) {
        VendorProfile profile = findProfileByIdOrUserId(id);
        VendorStatus current = profile.getStatus();

        if (current == VendorStatus.REJECTED) {
            log.info("[VendorProfileService] Vendor already rejected: id={}", profile.getId());
            return mapToResponse(profile);
        }

        if (!current.canTransitionTo(VendorStatus.REJECTED)) {
            throw new BadRequestException(String.format(
                    "Invalid state transition: Cannot reject vendor currently in '%s' status.", current));
        }

        profile.setStatus(VendorStatus.REJECTED);
        profile.setRejectionReason(reason != null ? reason.trim() : "Application does not meet marketplace standards");
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor REJECTED by admin {}: vendorId={}, reason={}",
                adminId, saved.getId(), profile.getRejectionReason());
        return mapToResponse(saved);
    }

    @Transactional
    public VendorProfileResponse suspendVendor(UUID id, UUID adminId, String reason) {
        VendorProfile profile = findProfileByIdOrUserId(id);
        VendorStatus current = profile.getStatus();

        if (current == VendorStatus.SUSPENDED) {
            log.info("[VendorProfileService] Vendor already suspended: id={}", profile.getId());
            return mapToResponse(profile);
        }

        if (!current.canTransitionTo(VendorStatus.SUSPENDED)) {
            throw new BadRequestException(String.format(
                    "Invalid state transition: Cannot suspend vendor currently in '%s' status.", current));
        }

        profile.setStatus(VendorStatus.SUSPENDED);
        profile.setSuspensionReason(reason != null ? reason.trim() : "Account suspended by platform administration");
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor SUSPENDED by admin {}: vendorId={}, reason={}",
                adminId, saved.getId(), profile.getSuspensionReason());
        return mapToResponse(saved);
    }

    @Transactional
    public VendorProfileResponse reactivateVendor(UUID id, UUID adminId) {
        VendorProfile profile = findProfileByIdOrUserId(id);
        VendorStatus current = profile.getStatus();

        if (current == VendorStatus.APPROVED) {
            log.info("[VendorProfileService] Vendor already active: id={}", profile.getId());
            return mapToResponse(profile);
        }

        if (!current.canTransitionTo(VendorStatus.APPROVED)) {
            throw new BadRequestException(String.format(
                    "Invalid state transition: Cannot reactivate vendor currently in '%s' status.", current));
        }

        profile.setStatus(VendorStatus.APPROVED);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setSuspensionReason(null);

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor REACTIVATED by admin {}: vendorId={}", adminId, saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public VendorStatusSummaryResponse getVendorStatusSummary(UUID userId) {
        Optional<VendorProfile> opt = vendorProfileRepository.findByUserId(userId);
        if (opt.isEmpty()) {
            return VendorStatusSummaryResponse.builder()
                    .userId(userId)
                    .status(VendorStatus.PENDING_APPROVAL)
                    .isApproved(false)
                    .build();
        }
        VendorProfile p = opt.get();
        return VendorStatusSummaryResponse.builder()
                .userId(p.getUserId())
                .storeName(p.getStoreName())
                .storeSlug(p.getStoreSlug())
                .status(p.getStatus())
                .isApproved(p.getStatus() == VendorStatus.APPROVED)
                .rejectionReason(p.getRejectionReason())
                .suspensionReason(p.getSuspensionReason())
                .build();
    }

    private VendorProfile findProfileByIdOrUserId(UUID id) {
        return vendorProfileRepository.findById(id)
                .or(() -> vendorProfileRepository.findByUserId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found with ID: " + id));
    }

    private String toSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }

    private String generateUniqueSlug(String baseSlug) {
        String cleanBase = (baseSlug == null || baseSlug.isBlank()) ? "store" : baseSlug;
        String slug = cleanBase;
        int count = 1;
        while (vendorProfileRepository.existsByStoreSlug(slug)) {
            slug = cleanBase + "-" + count;
            count++;
        }
        return slug;
    }

    private VendorProfileResponse mapToResponse(VendorProfile p) {
        String email = userProfileRepository.findByUserId(p.getUserId())
                .map(UserProfile::getEmail)
                .orElse(p.getSupportEmail());

        return VendorProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .email(email)
                .storeName(p.getStoreName())
                .storeSlug(p.getStoreSlug())
                .storeDescription(p.getStoreDescription())
                .logoUrl(p.getLogoUrl())
                .supportEmail(p.getSupportEmail())
                .businessTaxId(p.getBusinessTaxId())
                .payoutInfo(p.getPayoutInfo())
                .status(p.getStatus())
                .rejectionReason(p.getRejectionReason())
                .suspensionReason(p.getSuspensionReason())
                .reviewedBy(p.getReviewedBy())
                .reviewedAt(p.getReviewedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
