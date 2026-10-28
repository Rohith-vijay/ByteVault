package com.bytevault.identity.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.identity.dto.UpdateVendorProfileRequest;
import com.bytevault.identity.dto.VendorProfileResponse;
import com.bytevault.identity.dto.VendorStatusSummaryResponse;
import com.bytevault.identity.entity.VendorProfile;
import com.bytevault.identity.entity.VendorStatus;
import com.bytevault.identity.repository.VendorProfileRepository;
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
                .email(email != null ? email : "vendor-" + userId + "@bytevault.internal")
                .storeName(effectiveStoreName)
                .storeSlug(uniqueSlug)
                .storeDescription(storeDescription)
                .supportEmail(supportEmail != null ? supportEmail.trim() : email)
                .businessTaxId(businessTaxId)
                .payoutInfo(payoutInfo)
                .status(VendorStatus.PENDING)
                .build();

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Created new vendor profile in PENDING status: id={}, userId={}, store={}",
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
        return vendorProfileRepository.findAll().stream()
                .filter(p -> p.getStatus().isPending())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VendorProfileResponse> getAllVendors(VendorStatus statusFilter) {
        List<VendorProfile> list = (statusFilter != null)
                ? vendorProfileRepository.findByStatus(statusFilter)
                : vendorProfileRepository.findAll();
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VendorProfileResponse getVendorById(UUID id) {
        VendorProfile profile = vendorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found with ID: " + id));
        return mapToResponse(profile);
    }

    @Transactional
    public VendorProfileResponse approveVendor(UUID id, UUID adminId) {
        VendorProfile profile = vendorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found with ID: " + id));

        if (profile.getStatus() == VendorStatus.APPROVED) {
            throw new BadRequestException("Vendor is already in APPROVED status");
        }

        if (!profile.getStatus().canTransitionTo(VendorStatus.APPROVED)) {
            throw new BadRequestException("Illegal transition: Cannot approve vendor in status " + profile.getStatus());
        }

        profile.setStatus(VendorStatus.APPROVED);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setRejectionReason(null);

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor approved: id={}, userId={}, adminId={}", id, profile.getUserId(), adminId);
        return mapToResponse(saved);
    }

    @Transactional
    public VendorProfileResponse rejectVendor(UUID id, UUID adminId, String reason) {
        VendorProfile profile = vendorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found with ID: " + id));

        if (profile.getStatus() == VendorStatus.REJECTED) {
            throw new BadRequestException("Vendor is already in REJECTED status");
        }

        if (!profile.getStatus().canTransitionTo(VendorStatus.REJECTED)) {
            throw new BadRequestException("Illegal transition: Cannot reject vendor in status " + profile.getStatus());
        }

        profile.setStatus(VendorStatus.REJECTED);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setRejectionReason(reason);

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor application rejected: id={}, reason={}", id, reason);
        return mapToResponse(saved);
    }

    @Transactional
    public VendorProfileResponse suspendVendor(UUID id, UUID adminId, String reason) {
        VendorProfile profile = vendorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found with ID: " + id));

        if (profile.getStatus() == VendorStatus.SUSPENDED) {
            throw new BadRequestException("Vendor is already in SUSPENDED status");
        }

        if (!profile.getStatus().canTransitionTo(VendorStatus.SUSPENDED)) {
            throw new BadRequestException("Illegal transition: Cannot suspend vendor in status " + profile.getStatus());
        }

        profile.setStatus(VendorStatus.SUSPENDED);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setSuspensionReason(reason);

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor account suspended: id={}, reason={}", id, reason);
        return mapToResponse(saved);
    }

    @Transactional
    public VendorProfileResponse reactivateVendor(UUID id, UUID adminId) {
        VendorProfile profile = vendorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found with ID: " + id));

        if (profile.getStatus() == VendorStatus.APPROVED) {
            throw new BadRequestException("Vendor is already in APPROVED status");
        }

        if (!profile.getStatus().canTransitionTo(VendorStatus.APPROVED)) {
            throw new BadRequestException("Illegal transition: Cannot reactivate vendor in status " + profile.getStatus());
        }

        profile.setStatus(VendorStatus.APPROVED);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setSuspensionReason(null);

        VendorProfile saved = vendorProfileRepository.save(profile);
        log.info("[VendorProfileService] Vendor reactivated to APPROVED: id={}", id);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public VendorStatusSummaryResponse getVendorStatusSummary(UUID userId) {
        Optional<VendorProfile> profileOpt = vendorProfileRepository.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            return VendorStatusSummaryResponse.builder()
                    .userId(userId)
                    .status("NONE")
                    .approved(false)
                    .build();
        }

        VendorProfile p = profileOpt.get();
        return VendorStatusSummaryResponse.builder()
                .userId(userId)
                .status(p.getStatus().name())
                .approved(p.getStatus().isApproved())
                .storeName(p.getStoreName())
                .storeSlug(p.getStoreSlug())
                .build();
    }

    @Transactional(readOnly = true)
    public VendorProfileResponse getVendorBySlug(String slug) {
        VendorProfile profile = vendorProfileRepository.findByStoreSlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor store not found for slug: " + slug));
        return mapToResponse(profile);
    }

    private String toSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private String generateUniqueSlug(String baseSlug) {
        String slug = baseSlug;
        int counter = 1;
        while (vendorProfileRepository.existsByStoreSlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    public VendorProfileResponse mapToResponse(VendorProfile p) {
        return VendorProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .email(p.getEmail())
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
