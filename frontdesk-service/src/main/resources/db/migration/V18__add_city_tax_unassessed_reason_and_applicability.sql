-- E18 follow-up: close the "silent zero" gap in tourist-tax assessment.
--
-- Before this migration, CityTaxAssessmentServiceImpl#assessFor persisted no
-- row at all when the hotel's comune/category/rate wasn't configured (V17's
-- own comment on city_tax_assessments.city_tax_rate_id already documented the
-- intent to distinguish "assessed zero" from "unconfigured zero" — the code
-- just never did it). Result: a hotel that forgot to configure a rate had no
-- way to discover it short of manually reconciling every stay against the
-- monthly comune declaration. unassessed_reason makes that state queryable:
-- "which stays never had the tax assessed, and why" becomes a WHERE clause.

ALTER TABLE city_tax_assessments
    ADD COLUMN unassessed_reason VARCHAR(32);

COMMENT ON COLUMN city_tax_assessments.unassessed_reason IS
    'NULL when the tax was actually computed (even if the total is zero because '
    'every guest was exempt). Non-null names why nothing was assessed: '
    'COMUNE_NOT_CONFIGURED, CATEGORY_NOT_RECORDED, NO_RATE_FOR_DATE (all gaps a '
    'backfill can fix once configuration catches up), or NOT_APPLICABLE (the '
    'hotel has declared its comune does not levy the tax at all — never a gap).';

-- hotel_settings.city_tax_applicability: the tri-state that lets a hotel in a
-- comune with no tourist tax silence the "not configured" warnings for good,
-- without that silence also hiding hotels that simply forgot to configure a
-- real rate. UNKNOWN (the default) still warns; NOT_APPLICABLE is a deliberate
-- declaration that stops both the per-check-in and dashboard warnings.
ALTER TABLE hotel_settings
    ADD COLUMN city_tax_applicability VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

COMMENT ON COLUMN hotel_settings.city_tax_applicability IS
    'UNKNOWN (default) = not yet declared, still eligible for not-configured '
    'warnings. NOT_APPLICABLE = hotel has declared its comune does not levy a '
    'tourist tax; silences the warnings. APPLICABLE = declared applicable '
    '(informational; a configured rate is still required for assessment).';
