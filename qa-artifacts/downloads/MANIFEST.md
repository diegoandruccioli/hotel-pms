# Download verification manifest — 2026-08-24 QA pass

Every file below was captured via a real browser download event
(`context.on('download')` / Playwright's `page.waitForEvent('download')`), saved here, and
independently re-checked outside the test assertions (magic bytes via `xxd`, content spot-read by
hand). See `../REPORT.md` §5 for the narrative summary.

| File | Size | Trigger mechanism | Magic / structure check | Content check | Verdict |
|---|---:|---|---|---|---|
| `fattura-201e1176-6843-41dc-9d3f-13e27829023d.pdf` | 26,930 B | hidden `<iframe>` → `GET /invoices/{id}/pdf` | `%PDF-1.6` ✅ | Real invoice content, non-trivial size | ✅ |
| `fattura-33ff0b2e-53ac-4925-bae5-dd191aa8f80a.pdf` | 26,895 B | same | `%PDF-1.6` ✅ | — | ✅ |
| `fattura-81a119a6-de40-4bae-b868-a86c098a00ac.pdf` | 26,845 B | same | `%PDF-1.6` ✅ | — | ✅ |
| `fatturaPA-201e1176-6843-41dc-9d3f-13e27829023d.xml` | 3,479 B | validate-then-`<iframe>` → `GET /invoices/{id}/fatturaPA` | `<?xml ...` well-formed, `versione="FPR12"` ✅ | Real progressive invoice number, real guest `CodiceFiscale` (`RSSMRA90A01H501U`), correct `AliquotaIVA` split (10% room / 0% city-tax with `Natura=N1`), no placeholder anagraphics in `CedentePrestatore` | ✅ |
| `fatturaPA-33ff0b2e-53ac-4925-bae5-dd191aa8f80a.xml` | 3,479 B | same | ✅ | ✅ | ✅ |
| `fatturaPA-81a119a6-de40-4bae-b868-a86c098a00ac.xml` | 3,479 B | same | ✅ | ✅ | ✅ |
| `owner-report-2026-08-01-to-2026-08-24.csv` | 3,296 B | blob + `<a>` click → `GET /reports/owner` | UTF-8 BOM (`EF BB BF`) ✅, `;` separator ✅ | English headers/status (default locale — expected, see REPORT.md §Methodology); **re-verified separately in explicit IT locale**: `"N° Fattura";"Data Emissione";"Importo";"Stato";"ID Ospite"`, `"Pagata"`/`"Emessa"` — not saved to this directory (throwaway diagnostic run), but confirmed correct | ✅ |
| `preventivo-447320bb-76f2-4cf0-808e-654bf6e9034e.pdf` | 22,015 B | hidden `<iframe>` → `GET /quotations/{id}/pdf` | `%PDF` ✅ | — | ✅ |
| `preventivo-d3072550-3b70-45b7-b6a4-4e3b43b71969.pdf` | 22,133 B | same | `%PDF` ✅ | — | ✅ |
| `preventivo-ea84232b-0e6a-446e-9414-0df664f8f907.pdf` | 22,133 B | same | `%PDF` ✅ | — | ✅ |

## Not downloadable this session

| Expected file | Endpoint | Status |
|---|---|---|
| `alloggiati-{date}.txt` | `GET /stays/reports/alloggiati` | 🔴 Blocked — backend returns `422 ALLOGGIATI_FAMILIARE_WITHOUT_CAPO` for every attempt this session (real, root-caused finding — see `REPORT.md` §6 #3, not a download-mechanism failure per se) |
| `alloggiati-{date}.json` | `GET /stays/reports/alloggiati/json` | 🔴 Same blocker |

All filenames match the pattern documented in the respective backend controllers exactly
(`fattura-{uuid}.pdf`, `fatturaPA-{uuid}.xml`, `preventivo-{uuid}.pdf`,
`owner-report-{from}-to-{to}.csv`) — no mismatches found. All files landed in the expected local
download path (`qa-artifacts/downloads/`, this directory) with no misdirected saves.
