package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Single source of truth for splitting a gross charge amount into imponibile
 * (taxable base) and imposta (VAT) — shared by {@code PdfInvoiceServiceImpl}
 * (courtesy-copy PDF) and {@code FatturaPAServiceImpl} (FatturaPA XML) so the
 * two documents for the same invoice can never disagree on VAT figures.
 */
@Component
public class VatBreakdownCalculator {

    /**
     * Splits a single gross amount into taxable base and VAT at the given rate.
     * VAT is derived by subtraction ({@code gross - taxable}), not an independent
     * rounded multiplication, so {@code taxable + vat == gross} holds exactly.
     *
     * @param grossAmount the gross (VAT-included) amount
     * @param vatRate     the VAT rate (e.g. {@code 0.10}, {@code 0.22})
     * @return the taxable/VAT split for this charge
     */
    public VatLine splitLine(final BigDecimal grossAmount, final BigDecimal vatRate) {
        final BigDecimal taxable = grossAmount.divide(BigDecimal.ONE.add(vatRate), 2, RoundingMode.HALF_UP);
        final BigDecimal vat = grossAmount.subtract(taxable);
        return new VatLine(taxable, vat);
    }

    /**
     * Groups charges by VAT treatment — the {@code (rate, naturaCode)} pair, not rate
     * alone — summing the taxable/VAT split of each line. FatturaPA requires one
     * {@code DatiRiepilogo} block per distinct {@code (AliquotaIVA, Natura)}
     * combination: two 0%-rate lines with different {@code Natura} codes (e.g. a
     * future {@code N1} block and an {@code N4} block) are legally distinct and must
     * never merge, even though they'd collide under a rate-only key. Ordered by
     * ascending {@link VatGroupKey} (rate, then natura with nulls first) for stable
     * rendering in both the PDF summary table and the FatturaPA blocks.
     *
     * @param charges the invoice charges — each charge's {@code vatRate} is trusted
     *                non-null, guaranteed by the {@code invoice_charges.vat_rate}
     *                {@code NOT NULL} constraint (the only charge-creation path,
     *                {@code InvoiceServiceImpl.addCharge}, always sets it)
     * @return the per-treatment breakdown, empty if {@code charges} is null or empty
     */
    public Map<VatGroupKey, VatLine> groupByTreatment(final List<ChargeResponse> charges) {
        final Map<VatGroupKey, VatLine> breakdown = new TreeMap<>();
        if (charges == null) {
            return breakdown;
        }
        for (final ChargeResponse charge : charges) {
            final VatLine line = splitLine(charge.amount(), charge.vatRate());
            final VatGroupKey key = new VatGroupKey(charge.vatRate(), charge.naturaCode());
            breakdown.merge(key, line,
                    (a, b) -> new VatLine(a.taxable().add(b.taxable()), a.vat().add(b.vat())));
        }
        return breakdown;
    }

    /**
     * Verifies that the sum of all charge amounts matches the invoice's persisted
     * total. This can never fail today — VAT is derived by subtraction and
     * {@code totalAmount} is the running sum of charge amounts maintained by
     * {@code InvoiceServiceImpl.addCharge} — but acts as a regression safety net:
     * a future change to either derivation must not be able to silently break
     * the invariant that the PDF and the FatturaPA XML always show the same total.
     *
     * @param totalAmount the invoice's persisted total
     * @param charges     the invoice's charges
     * @throws BillingValidationException if the sums disagree
     */
    public void assertReconciles(final BigDecimal totalAmount, final List<ChargeResponse> charges) {
        if (charges == null || charges.isEmpty()) {
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final ChargeResponse charge : charges) {
            sum = sum.add(charge.amount());
        }
        final BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        if (sum.compareTo(total) != 0) {
            throw new BillingValidationException(
                    "INVOICE_TOTAL_MISMATCH: charges sum=" + sum + " but invoice totalAmount=" + total);
        }
    }

    /**
     * Taxable base and VAT for one charge or one VAT-treatment group.
     *
     * @param taxable the taxable base (imponibile)
     * @param vat     the VAT amount (imposta)
     */
    public record VatLine(BigDecimal taxable, BigDecimal vat) {
    }

    /**
     * The FatturaPA {@code DatiRiepilogo} grouping key: VAT rate plus the optional
     * {@code Natura} code. Two lines at the same rate but different (or absent)
     * {@code naturaCode} are legally distinct treatments and must land in separate
     * {@code DatiRiepilogo} blocks — this is why grouping can't key on rate alone.
     * Ordered by rate first, then {@code naturaCode} with {@code null} (ordinary
     * taxable lines) sorting before any natura code.
     *
     * @param rate       the VAT rate
     * @param naturaCode the FatturaPA {@code Natura} code, or {@code null} for an
     *                   ordinary taxable line
     */
    public record VatGroupKey(BigDecimal rate, String naturaCode) implements Comparable<VatGroupKey> {

        private static final Comparator<VatGroupKey> ORDER = Comparator
                .comparing(VatGroupKey::rate)
                .thenComparing(VatGroupKey::naturaCode, Comparator.nullsFirst(Comparator.naturalOrder()));

        @Override
        public int compareTo(final VatGroupKey other) {
            return ORDER.compare(this, other);
        }
    }
}
