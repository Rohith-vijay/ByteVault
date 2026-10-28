package com.bytevault.payment.service;

import com.bytevault.payment.dto.VendorEarningsResponse;
import com.bytevault.payment.entity.VendorLedger;
import com.bytevault.payment.repository.VendorLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorLedgerService {

    private final VendorLedgerRepository ledgerRepository;
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10"); // 10%

    @Transactional
    public VendorLedger recordTransaction(UUID vendorId, UUID orderId, BigDecimal grossAmount) {
        if (vendorId == null || orderId == null || grossAmount == null) {
            throw new IllegalArgumentException("vendorId, orderId, and grossAmount must not be null");
        }
        if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gross amount must be positive.");
        }

        // Double-crediting protection: idempotently return existing entry if already credited
        java.util.Optional<VendorLedger> existing = ledgerRepository.findByOrderIdAndVendorId(orderId, vendorId);
        if (existing.isPresent()) {
            log.info("[VendorLedgerService] Idempotent skip: ledger entry already exists for orderId={} and vendorId={}", orderId, vendorId);
            return existing.get();
        }

        BigDecimal platformFee = grossAmount.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(platformFee);

        VendorLedger ledger = VendorLedger.builder()
                .vendorId(vendorId)
                .orderId(orderId)
                .grossAmount(grossAmount)
                .platformFee(platformFee)
                .netAmount(netAmount)
                .status("AVAILABLE")
                .build();

        try {
            VendorLedger saved = ledgerRepository.save(ledger);
            log.info("[VendorLedgerService] Recorded ledger entry: id={}, vendor={}, gross={}, net={}",
                    saved.getId(), vendorId, grossAmount, netAmount);
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.warn("[VendorLedgerService] Concurrent race detected on orderId={} and vendorId={}. Recovering existing record.", orderId, vendorId);
            return ledgerRepository.findByOrderIdAndVendorId(orderId, vendorId)
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    public void reverseTransactionForRefund(UUID orderId, BigDecimal refundAmount, boolean isFullRefund) {
        if (orderId == null) {
            log.warn("[VendorLedgerService] reverseTransactionForRefund called with null orderId");
            return;
        }

        List<VendorLedger> entries = ledgerRepository.findByOrderId(orderId);
        if (entries.isEmpty()) {
            log.warn("[VendorLedgerService] No ledger entries found for orderId={} to reverse", orderId);
            return;
        }

        if (isFullRefund) {
            for (VendorLedger entry : entries) {
                entry.setStatus("REFUNDED");
                entry.setRefundedAmount(entry.getGrossAmount());
                ledgerRepository.save(entry);
                log.info("[VendorLedgerService] Full refund applied to ledger entry {}: status=REFUNDED, refundedAmount={}",
                        entry.getId(), entry.getGrossAmount());
            }
        } else {
            BigDecimal totalGross = entries.stream()
                    .map(VendorLedger::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalGross.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal remainingRefund = refundAmount != null ? refundAmount : BigDecimal.ZERO;
            for (VendorLedger entry : entries) {
                BigDecimal proportion = entry.getGrossAmount().divide(totalGross, 4, RoundingMode.HALF_UP);
                BigDecimal vendorRefund = remainingRefund.multiply(proportion).setScale(2, RoundingMode.HALF_UP);

                BigDecimal currentRefunded = entry.getRefundedAmount() != null ? entry.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal newRefunded = currentRefunded.add(vendorRefund).min(entry.getGrossAmount());
                entry.setRefundedAmount(newRefunded);
                if (newRefunded.compareTo(entry.getGrossAmount()) >= 0) {
                    entry.setStatus("REFUNDED");
                } else {
                    entry.setStatus("PARTIALLY_REFUNDED");
                }
                ledgerRepository.save(entry);
                log.info("[VendorLedgerService] Partial refund applied to ledger entry {}: status={}, refundedAmount={}",
                        entry.getId(), entry.getStatus(), newRefunded);
            }
        }
    }

    @Transactional(readOnly = true)
    public VendorEarningsResponse getVendorEarnings(UUID vendorId) {
        List<VendorLedger> entries = ledgerRepository.findByVendorIdOrderByCreatedAtDesc(vendorId);

        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal feeTotal = BigDecimal.ZERO;
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal availableTotal = BigDecimal.ZERO;
        BigDecimal pendingTotal = BigDecimal.ZERO;

        for (VendorLedger entry : entries) {
            if ("REFUNDED".equalsIgnoreCase(entry.getStatus())) {
                continue;
            }

            BigDecimal effectiveGross = entry.getGrossAmount();
            BigDecimal effectiveFee = entry.getPlatformFee();
            BigDecimal effectiveNet = entry.getNetAmount();

            if ("PARTIALLY_REFUNDED".equalsIgnoreCase(entry.getStatus()) && entry.getRefundedAmount() != null) {
                effectiveGross = effectiveGross.subtract(entry.getRefundedAmount()).max(BigDecimal.ZERO);
                effectiveFee = effectiveGross.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
                effectiveNet = effectiveGross.subtract(effectiveFee);
            }

            grossTotal = grossTotal.add(effectiveGross);
            feeTotal = feeTotal.add(effectiveFee);
            netTotal = netTotal.add(effectiveNet);

            if ("AVAILABLE".equalsIgnoreCase(entry.getStatus()) || "PARTIALLY_REFUNDED".equalsIgnoreCase(entry.getStatus())) {
                availableTotal = availableTotal.add(effectiveNet);
            } else if ("PENDING_SETTLEMENT".equalsIgnoreCase(entry.getStatus())) {
                pendingTotal = pendingTotal.add(effectiveNet);
            }
        }

        List<VendorEarningsResponse.VendorLedgerItemDto> itemDtos = entries.stream()
                .map(e -> VendorEarningsResponse.VendorLedgerItemDto.builder()
                        .id(e.getId())
                        .orderId(e.getOrderId())
                        .grossAmount(e.getGrossAmount())
                        .platformFee(e.getPlatformFee())
                        .netAmount(e.getNetAmount())
                        .refundedAmount(e.getRefundedAmount() != null ? e.getRefundedAmount() : BigDecimal.ZERO)
                        .status(e.getStatus())
                        .createdAt(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return VendorEarningsResponse.builder()
                .vendorId(vendorId)
                .grossEarnings(grossTotal)
                .platformFees(feeTotal)
                .netEarnings(netTotal)
                .availableBalance(availableTotal)
                .pendingSettlement(pendingTotal)
                .totalOrdersCount(entries.size())
                .ledgerEntries(itemDtos)
                .build();
    }
}
