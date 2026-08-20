package com.hotelpms.billing.service;

import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VatBreakdownCalculatorTest {

    private static final BigDecimal VAT_RATE_10 = new BigDecimal("0.10");
    private static final BigDecimal VAT_RATE_22 = new BigDecimal("0.22");
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final BigDecimal GROSS_110 = new BigDecimal("110.00");
    private static final BigDecimal UNRELATED_TOTAL = new BigDecimal("999.00");
    private static final BigDecimal AMOUNT_12_20 = new BigDecimal("12.20");
    private static final BigDecimal AMOUNT_6 = new BigDecimal("6.00");
    private static final String NATURA_N1 = "N1";
    private static final String NATURA_N4 = "N4";

    private final VatBreakdownCalculator calculator = new VatBreakdownCalculator();

    @Test
    void splitLineReconstructsGrossAmountExactly() {
        final VatBreakdownCalculator.VatLine line = calculator.splitLine(GROSS_110, VAT_RATE_10);

        assertThat(line.taxable()).isEqualByComparingTo("100.00");
        assertThat(line.vat()).isEqualByComparingTo("10.00");
        assertThat(line.taxable().add(line.vat())).isEqualByComparingTo(GROSS_110);
    }

    @Test
    void splitLineAtZeroRateReturnsFullGrossAsTaxableAndZeroVat() {
        final VatBreakdownCalculator.VatLine line = calculator.splitLine(GROSS_110, BigDecimal.ZERO);

        assertThat(line.taxable()).isEqualByComparingTo(GROSS_110);
        assertThat(line.vat()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void groupByTreatmentSumsChargesWithTheSameRate() {
        final List<ChargeResponse> charges = List.of(
                charge(GROSS_110, VAT_RATE_10),
                charge(new BigDecimal("55.00"), VAT_RATE_10),
                charge(AMOUNT_12_20, VAT_RATE_22));

        final Map<VatBreakdownCalculator.VatGroupKey, VatBreakdownCalculator.VatLine> breakdown =
                calculator.groupByTreatment(charges);

        assertThat(breakdown).hasSize(2);
        final VatBreakdownCalculator.VatGroupKey rate10 = new VatBreakdownCalculator.VatGroupKey(VAT_RATE_10, null);
        final VatBreakdownCalculator.VatGroupKey rate22 = new VatBreakdownCalculator.VatGroupKey(VAT_RATE_22, null);
        assertThat(breakdown.get(rate10).taxable()).isEqualByComparingTo("150.00");
        assertThat(breakdown.get(rate10).vat()).isEqualByComparingTo("15.00");
        assertThat(breakdown.get(rate22).taxable()).isEqualByComparingTo("10.00");
        assertThat(breakdown.get(rate22).vat()).isEqualByComparingTo("2.20");
    }

    @Test
    void groupByTreatmentReturnsEmptyMapForNullOrEmptyCharges() {
        assertThat(calculator.groupByTreatment(null)).isEmpty();
        assertThat(calculator.groupByTreatment(List.of())).isEmpty();
    }

    @Test
    void groupByTreatmentDoesNotMergeSameRateWithDifferentNaturaCodes() {
        final Map<VatBreakdownCalculator.VatGroupKey, VatBreakdownCalculator.VatLine> breakdown =
                calculator.groupByTreatment(List.of(
                        chargeWithNatura(AMOUNT_6, NATURA_N1),
                        chargeWithNatura(new BigDecimal("4.00"), NATURA_N4)));

        assertThat(breakdown).hasSize(2);
        final VatBreakdownCalculator.VatGroupKey n1 = new VatBreakdownCalculator.VatGroupKey(BigDecimal.ZERO, NATURA_N1);
        final VatBreakdownCalculator.VatGroupKey n4 = new VatBreakdownCalculator.VatGroupKey(BigDecimal.ZERO, NATURA_N4);
        assertThat(breakdown.get(n1).taxable()).isEqualByComparingTo(AMOUNT_6);
        assertThat(breakdown.get(n4).taxable()).isEqualByComparingTo("4.00");
    }

    @Test
    void groupByTreatmentOrdersByRateThenNaturaWithNullsFirst() {
        final List<ChargeResponse> charges = List.of(
                chargeWithNatura(AMOUNT_6, NATURA_N4),
                charge(AMOUNT_12_20, VAT_RATE_22),
                chargeWithNatura(AMOUNT_6, NATURA_N1),
                charge(GROSS_110, VAT_RATE_10));

        final List<VatBreakdownCalculator.VatGroupKey> orderedKeys =
                List.copyOf(calculator.groupByTreatment(charges).keySet());

        // 0% (natura-bearing) sorts before any positive rate; among equal 0% rates,
        // natura codes sort by natural String order ("N1" before "N4").
        assertThat(orderedKeys).containsExactly(
                new VatBreakdownCalculator.VatGroupKey(BigDecimal.ZERO, NATURA_N1),
                new VatBreakdownCalculator.VatGroupKey(BigDecimal.ZERO, NATURA_N4),
                new VatBreakdownCalculator.VatGroupKey(VAT_RATE_10, null),
                new VatBreakdownCalculator.VatGroupKey(VAT_RATE_22, null));
    }

    @Test
    void assertReconcilesPassesWhenSumMatchesTotal() {
        final List<ChargeResponse> charges = List.of(
                charge(GROSS_110, VAT_RATE_10),
                charge(AMOUNT_12_20, VAT_RATE_22));

        calculator.assertReconciles(new BigDecimal("122.20"), charges);
    }

    @Test
    void assertReconcilesPassesWithANaturaLinePresent() {
        final List<ChargeResponse> charges = List.of(
                charge(GROSS_110, VAT_RATE_10),
                chargeWithNatura(AMOUNT_6, NATURA_N1));

        calculator.assertReconciles(new BigDecimal("116.00"), charges);
    }

    @Test
    void assertReconcilesIsANoOpForNullOrEmptyCharges() {
        calculator.assertReconciles(UNRELATED_TOTAL, null);
        calculator.assertReconciles(UNRELATED_TOTAL, List.of());
    }

    @Test
    void assertReconcilesThrowsWhenSumDisagreesWithTotal() {
        final List<ChargeResponse> charges = List.of(charge(GROSS_110, VAT_RATE_10));

        assertThatThrownBy(() -> calculator.assertReconciles(UNRELATED_TOTAL, charges))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("INVOICE_TOTAL_MISMATCH");
    }

    private static ChargeResponse charge(final BigDecimal amount, final BigDecimal vatRate) {
        return new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.ROOM_NIGHT,
                "Test charge", amount, vatRate, null, null, null, null, LocalDateTime.now());
    }

    private static ChargeResponse chargeWithNatura(final BigDecimal amount, final String naturaCode) {
        return new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.ROOM_NIGHT,
                "Natura test charge", amount, BigDecimal.ZERO, naturaCode, null, null, null, LocalDateTime.now());
    }
}
