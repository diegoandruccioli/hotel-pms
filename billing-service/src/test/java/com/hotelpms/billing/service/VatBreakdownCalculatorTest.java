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

    private final VatBreakdownCalculator calculator = new VatBreakdownCalculator();

    @Test
    void splitLineReconstructsGrossAmountExactly() {
        final VatBreakdownCalculator.VatLine line = calculator.splitLine(GROSS_110, VAT_RATE_10);

        assertThat(line.taxable()).isEqualByComparingTo("100.00");
        assertThat(line.vat()).isEqualByComparingTo("10.00");
        assertThat(line.taxable().add(line.vat())).isEqualByComparingTo(GROSS_110);
    }

    @Test
    void groupByRateSumsChargesWithTheSameRate() {
        final List<ChargeResponse> charges = List.of(
                charge(GROSS_110, VAT_RATE_10),
                charge(new BigDecimal("55.00"), VAT_RATE_10),
                charge(new BigDecimal("12.20"), VAT_RATE_22));

        final Map<BigDecimal, VatBreakdownCalculator.VatLine> breakdown = calculator.groupByRate(charges);

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown.get(VAT_RATE_10).taxable()).isEqualByComparingTo("150.00");
        assertThat(breakdown.get(VAT_RATE_10).vat()).isEqualByComparingTo("15.00");
        assertThat(breakdown.get(VAT_RATE_22).taxable()).isEqualByComparingTo("10.00");
        assertThat(breakdown.get(VAT_RATE_22).vat()).isEqualByComparingTo("2.20");
    }

    @Test
    void groupByRateReturnsEmptyMapForNullOrEmptyCharges() {
        assertThat(calculator.groupByRate(null)).isEmpty();
        assertThat(calculator.groupByRate(List.of())).isEmpty();
    }

    @Test
    void assertReconcilesPassesWhenSumMatchesTotal() {
        final List<ChargeResponse> charges = List.of(
                charge(GROSS_110, VAT_RATE_10),
                charge(new BigDecimal("12.20"), VAT_RATE_22));

        calculator.assertReconciles(new BigDecimal("122.20"), charges);
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
                "Test charge", amount, vatRate, null, LocalDateTime.now());
    }
}
