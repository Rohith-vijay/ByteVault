package com.bytevault.payment;

import com.bytevault.payment.dto.VendorEarningsResponse;
import com.bytevault.payment.entity.VendorLedger;
import com.bytevault.payment.repository.VendorLedgerRepository;
import com.bytevault.payment.service.VendorLedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VendorLedgerServiceTest {

    @Mock
    private VendorLedgerRepository ledgerRepository;

    @InjectMocks
    private VendorLedgerService vendorLedgerService;

    @Test
    @DisplayName("Record transaction calculates 10% platform fee and 90% net amount precisely")
    void testRecordTransaction_CommissionAndNetPrecision() {
        UUID vendorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal gross = new BigDecimal("1000.00");

        when(ledgerRepository.findByOrderIdAndVendorId(orderId, vendorId)).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(VendorLedger.class))).thenAnswer(i -> {
            VendorLedger l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        VendorLedger result = vendorLedgerService.recordTransaction(vendorId, orderId, gross);

        assertNotNull(result);
        assertEquals(new BigDecimal("1000.00"), result.getGrossAmount());
        assertEquals(new BigDecimal("100.00"), result.getPlatformFee());
        assertEquals(new BigDecimal("900.00"), result.getNetAmount());
        assertEquals("AVAILABLE", result.getStatus());

        verify(ledgerRepository, times(1)).save(any(VendorLedger.class));
    }

    @Test
    @DisplayName("Record transaction calculates fractional cents with half-up rounding")
    void testRecordTransaction_FractionalRounding() {
        UUID vendorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal gross = new BigDecimal("149.99");

        when(ledgerRepository.findByOrderIdAndVendorId(orderId, vendorId)).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(VendorLedger.class))).thenAnswer(i -> i.getArgument(0));

        VendorLedger result = vendorLedgerService.recordTransaction(vendorId, orderId, gross);

        assertNotNull(result);
        // 149.99 * 0.10 = 14.999 -> rounds to 15.00
        assertEquals(new BigDecimal("15.00"), result.getPlatformFee());
        // 149.99 - 15.00 = 134.99
        assertEquals(new BigDecimal("134.99"), result.getNetAmount());
    }

    @Test
    @DisplayName("Double crediting prevention: Retried or duplicate event returns existing ledger entry")
    void testRecordTransaction_PreventsDoubleCrediting() {
        UUID vendorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal gross = new BigDecimal("500.00");

        VendorLedger existingLedger = VendorLedger.builder()
                .id(UUID.randomUUID())
                .vendorId(vendorId)
                .orderId(orderId)
                .grossAmount(gross)
                .platformFee(new BigDecimal("50.00"))
                .netAmount(new BigDecimal("450.00"))
                .status("AVAILABLE")
                .build();

        when(ledgerRepository.findByOrderIdAndVendorId(orderId, vendorId)).thenReturn(Optional.of(existingLedger));

        VendorLedger result = vendorLedgerService.recordTransaction(vendorId, orderId, gross);

        assertNotNull(result);
        assertEquals(existingLedger.getId(), result.getId());
        assertEquals(new BigDecimal("450.00"), result.getNetAmount());

        // Ensure save was NEVER called again
        verify(ledgerRepository, never()).save(any(VendorLedger.class));
    }

    @Test
    @DisplayName("Invalid gross amount (negative or zero) -> Throws IllegalArgumentException")
    void testRecordTransaction_InvalidAmount_ThrowsException() {
        UUID vendorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                vendorLedgerService.recordTransaction(vendorId, orderId, BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class, () ->
                vendorLedgerService.recordTransaction(vendorId, orderId, new BigDecimal("-50.00")));
    }

    @Test
    @DisplayName("Get vendor earnings aggregates totals correctly")
    void testGetVendorEarnings_AggregatesCorrectly() {
        UUID vendorId = UUID.randomUUID();

        VendorLedger entry1 = VendorLedger.builder()
                .id(UUID.randomUUID())
                .vendorId(vendorId)
                .orderId(UUID.randomUUID())
                .grossAmount(new BigDecimal("200.00"))
                .platformFee(new BigDecimal("20.00"))
                .netAmount(new BigDecimal("180.00"))
                .status("AVAILABLE")
                .build();

        VendorLedger entry2 = VendorLedger.builder()
                .id(UUID.randomUUID())
                .vendorId(vendorId)
                .orderId(UUID.randomUUID())
                .grossAmount(new BigDecimal("100.00"))
                .platformFee(new BigDecimal("10.00"))
                .netAmount(new BigDecimal("90.00"))
                .status("PENDING_SETTLEMENT")
                .build();

        when(ledgerRepository.findByVendorIdOrderByCreatedAtDesc(vendorId))
                .thenReturn(List.of(entry1, entry2));

        VendorEarningsResponse response = vendorLedgerService.getVendorEarnings(vendorId);

        assertNotNull(response);
        assertEquals(vendorId, response.getVendorId());
        assertEquals(new BigDecimal("300.00"), response.getGrossEarnings());
        assertEquals(new BigDecimal("30.00"), response.getPlatformFees());
        assertEquals(new BigDecimal("270.00"), response.getNetEarnings());
        assertEquals(new BigDecimal("180.00"), response.getAvailableBalance());
        assertEquals(new BigDecimal("90.00"), response.getPendingSettlement());
        assertEquals(2, response.getTotalOrdersCount());
        assertEquals(2, response.getLedgerEntries().size());
    }

    @Test
    @DisplayName("Concurrent write race condition: DataIntegrityViolationException is handled and existing record returned")
    void testRecordTransaction_ConcurrentRaceCondition_RecoversExistingRecord() {
        UUID vendorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal gross = new BigDecimal("500.00");

        VendorLedger savedLedger = VendorLedger.builder()
                .id(UUID.randomUUID())
                .vendorId(vendorId)
                .orderId(orderId)
                .grossAmount(gross)
                .platformFee(new BigDecimal("50.00"))
                .netAmount(new BigDecimal("450.00"))
                .status("AVAILABLE")
                .build();

        // 1st check returns empty (thread thought it was first)
        when(ledgerRepository.findByOrderIdAndVendorId(orderId, vendorId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedLedger));

        // DB save fails with unique constraint violation
        when(ledgerRepository.save(any(VendorLedger.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for key uk_vendor_order_ledger"));

        VendorLedger result = vendorLedgerService.recordTransaction(vendorId, orderId, gross);

        assertNotNull(result);
        assertEquals(savedLedger.getId(), result.getId());
        assertEquals(new BigDecimal("450.00"), result.getNetAmount());
    }
}
