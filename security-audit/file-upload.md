# File Upload & Path Traversal Audit

Documento di sola analisi. Nessun file di codice toccato per produrlo — solo
`security-audit/file-upload.md` è stato scritto.

---

## 1. Verifica upload file — confermato: NESSUN endpoint di upload esiste

Ricerche eseguite sull'intero repo (`**/*.java`, tutti i moduli):

- `MultipartFile|@RequestPart|multipart/form-data|FileUpload` → **0 risultati** in
  codice applicativo (Java). L'unico match testuale a "multipart" nel repo è in
  `THREAT_MODEL.md` (voce DEP-CVE-01, changelog Netty/Spring-Cloud-Gateway) e nei
  `build.gradle.kts` di 5 servizi, dove compare solo come commento/override di
  versione per `commons-fileupload:1.6.0` (fix CVE-2025-48976). Quel commento lo
  dichiara esplicitamente: *"commons-fileupload is not managed by Spring Boot 3.5.x
  BOM (removed with CommonsMultipartResolver in Spring 6.1)"* — è una dipendenza
  transitiva forzata per igiene CVE, non un componente usato dal codice applicativo.
  Nessun `MultipartResolver`/`CommonsMultipartResolver` configurato in nessun servizio.
- `InputStream` come parametro di metodo controller → **0 risultati**. L'unico uso di
  `InputStream` nel repo è interno (lettura di risorse XSD/font/logo dal classpath in
  `FatturaPaXsdValidator`, `ThymeleafPdfTemplateRenderer`, `PdfInvoiceServiceImpl`),
  mai come parametro HTTP in ingresso.
- Nessun `@RequestPart`, nessun `FilePart` (WebFlux), nessuna configurazione
  `spring.servlet.multipart.*` che abiliti un endpoint di upload.

### `GuestController.addDocument` — verificato: metadata-only, nessun contenuto binario

File: `guest-service/src/main/java/com/hotelpms/guest/controller/GuestController.java:141-147`

```java
@PostMapping("/{id}/documents")
public ResponseEntity<IdentityDocumentResponseDTO> addIdentityDocument(
        @NonNull @PathVariable final UUID id,
        @NonNull @Valid @RequestBody final IdentityDocumentRequestDTO request) {
    final IdentityDocumentResponseDTO response = guestService.addIdentityDocument(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

Il body è `application/json` (`@RequestBody`), non `multipart/form-data`. Il DTO
(`guest-service/.../dto/request/IdentityDocumentRequestDTO.java:23-33`) contiene
esclusivamente:

```java
public record IdentityDocumentRequestDTO(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max=...) @Pattern(...) String documentNumber,
        @NotNull @Past LocalDate issueDate,
        @NotNull @FutureOrPresent LocalDate expiryDate,
        @Size(max=...) @Pattern(...) String issuingCountry) { }
```

Nessun campo `byte[]`, nessuna stringa base64, nessun `fileName`/`contentType`. La
stessa forma si riflette nell'entity JPA persistita
(`guest-service/.../model/IdentityDocument.java:44-81`): `documentType`,
`documentNumber`, `issueDate`, `expiryDate`, `issuingCountry` — solo metadati del
documento d'identità (es. "carta d'identità n. AB1234567, scadenza 2030-01-01"),
mai il file/immagine del documento stesso. **Confermato: nessun upload binario,
nessun rischio di executable-upload / MIME-spoofing / oversized-file-DoS applicabile.**

### Conclusione

Il verdetto preliminare di `00-recon.md` è confermato con evidenza esaustiva: il
codebase non espone alcun endpoint di upload file, in nessun servizio. Il resto
dell'audit si concentra interamente sul lato generazione/download dei file (PDF
fattura/preventivo, XML FatturaPA, ZIP export, report Alloggiati) — l'unica
superficie di attacco file-correlata realmente presente.

---

## 2. Path traversal sul lato generazione/download file

**Risultato: nessun path traversal individuato.** Motivo strutturale, non solo
assenza di bug puntuali: **l'intero backend non costruisce mai un percorso
filesystem a partire da input della richiesta.**

Ricerche eseguite su tutto `**/*.java`:

- `new File(...)`, `Paths.get(...)`, `Path.of(...)`, `.resolve(...)`,
  `FileSystemResource` → **0 risultati** in codice applicativo. L'unico match di
  `ClassPathResource` è in `FatturaPaXsdValidator` (schema XSD fisso, vedi §2.2).
- `getResourceAsStream(...)` → solo 2 usi, entrambi con **stringa costante
  hard-coded**, mai da parametro (vedi §2.2, §2.3).
- Nessun `addResourceHandler`/`ResourceHandlerRegistry` custom in nessun servizio
  (niente static-resource-serving configurabile da path esterno).

In pratica: ogni "file" generato dal backend (PDF, XML, ZIP, TXT/JSON Alloggiati) è
prodotto **interamente in memoria** (`byte[]`, `ByteArrayOutputStream`,
`StringBuilder`) e restituito direttamente nel body della risposta HTTP — non
esiste un secondo passaggio "scrivi su disco poi rileggi/servi", che è il pattern
classico dove il path traversal si annida. Di seguito il dettaglio per ciascun
endpoint elencato nel task.

### 2.1 `{id}` usato per generare PDF/XML — sempre e solo chiave di lookup DB

| Endpoint | Controller | Tipizzazione `{id}` | Uso |
|---|---|---|---|
| `GET /api/v1/invoices/{id}/pdf` | `InvoiceController.java:212` | `@PathVariable UUID id` | `pdfInvoiceService.generateInvoicePdf(id)` → `invoiceService.getInvoice(invoiceId)` (repo JPA) |
| `GET /api/v1/invoices/{id}/fatturaPA` | `InvoiceController.java:250` | `@PathVariable UUID id` | `fatturaPAService.generateXml(id)` → lookup DB |
| `GET /api/v1/quotations/{id}/pdf` | `QuotationController.java:109-117` | `@PathVariable UUID id` | `quotationService.getQuotationPdf(id)` → lookup DB |

In tutti e tre i casi:

1. **Il tipo del path variable è `UUID`, non `String`.** Spring MVC esegue la
   conversione tramite `Converter<String, UUID>` prima ancora che il metodo del
   controller venga invocato: un payload come `../../../etc/passwd` o
   `..%2f..%2fetc%2fpasswd` **non supera nemmeno il binding** — la richiesta
   fallisce con `400 Bad Request` (`MethodArgumentTypeMismatchException`) prima di
   raggiungere qualunque logica applicativo. Questo è strutturalmente diverso da
   un `@PathVariable String id` seguito da una validazione UUID manuale (dove un
   controllo dimenticato sarebbe stato un vero gap) — qui l'incapacità di accettare
   un valore non-UUID è imposta dal framework, non da un controllo applicativo.
2. **`id` non tocca mai il filesystem.** `PdfInvoiceServiceImpl.generateInvoicePdf`
   (righe 71-83) lo passa a `invoiceService.getInvoice(invoiceId)` — una query JPA
   parametrizzata (già verificato in un audit injection precedente in questa
   sessione: zero concatenazione SQL/JPQL in tutto il repo) — e usa il risultato
   solo per popolare un `Map<String,Object> context` passato al template Thymeleaf.
   Stesso pattern in `FatturaPAServiceImpl` e `QuotationServiceImpl`.
3. **`id` compare anche nel nome del file scaricato**
   (`ContentDisposition.attachment().filename(PDF_FILENAME_PREFIX + id + PDF_EXTENSION)`,
   `InvoiceController.java:213-215` e analoghi) — ma questo è **solo l'header
   `Content-Disposition` della risposta HTTP**, un suggerimento di nome file per il
   browser del client, mai un percorso letto/scritto sul filesystem del server.
   Anche se fosse manipolabile (non lo è, essendo un `UUID` tipizzato), l'impatto
   sarebbe al più un nome-file-suggerito anomalo lato client, non una lettura
   arbitraria lato server.

**Nessun exploit costruibile**: non esiste un valore di `id` che produca lettura di
file arbitrari, perché (a) il binding a `UUID` rifiuta qualunque payload non-UUID
prima di eseguire codice applicativo, e (b) anche un UUID valido ma inesistente
risulta in un lookup DB fallito (404/eccezione), mai in un accesso a filesystem.

### 2.2 `pdf-template-engine` / `ThymeleafPdfTemplateRenderer` — nome template mai da input utente

File: `pdf-template-engine/src/main/java/com/hotelpms/pdftemplate/ThymeleafPdfTemplateRenderer.java:74-97`

```java
public byte[] render(final String templateName, final Map<String, Object> context) {
    ...
    final String html = templateEngine.process(templateName, thymeleafContext);
    ...
}
```

Il parametro `templateName` esiste come stringa generica nella libreria, ma **ogni
chiamante nel repo lo passa come costante fissa**, mai come valore derivato da
richiesta HTTP:

- `billing-service/.../PdfInvoiceServiceImpl.java:85-87` — `templateFor(DocumentType docType)`
  è uno switch a due sole uscite: `docType == DocumentType.FATTURA ? TEMPLATE_FATTURA
  : TEMPLATE_RICEVUTA`, dove `TEMPLATE_FATTURA`/`TEMPLATE_RICEVUTA` sono
  `private static final String` (righe 48-49). `docType` stesso proviene da
  `invoice.documentType()`, un **enum Java** persistito lato server — non una
  stringa libera dal client. Nessun percorso da 2 valori fissi a un file arbitrario.
- `frontdesk-service/.../QuotationServiceImpl.java:520` — `pdfTemplateRenderer.render(PDF_TEMPLATE, context)`
  dove `PDF_TEMPLATE = "quotation"` (riga 66) è una costante hard-coded, nessuna
  variabile.

Inoltre `ClassLoaderTemplateResolver` (righe 62-70) risolve solo risorse sotto un
prefisso classpath fisso con suffisso `.html` forzato — anche ammesso (ipoteticamente,
non è il caso qui) che `templateName` fosse manipolabile, resterebbe confinato al
classpath dell'applicazione, non al filesystem reale.

**Riconferma della claim di recon sul locale allowlist** (per completezza, anche se
riguarda `notification-service`, non `pdf-template-engine`): in
`notification-service/src/main/java/com/hotelpms/notification/service/impl/NotificationServiceImpl.java:162-164`

```java
private static String sanitizeLocale(final String locale) {
    return ENGLISH_LOCALE.equals(locale) ? ENGLISH_LOCALE : DEFAULT_LOCALE;
}
```

è un allowlist stretto a due soli output possibili (`"en"` o `"it"`, quest'ultimo
come default per qualunque valore non riconosciuto) usato per comporre nomi di
template Thymeleaf (`"email/reservation-confirmed-" + sanitizeLocale(...)`, righe
63/76/89/103) — **claim di recon riverificata e confermata sul codice attuale**:
qualunque valore di `locale` (incluso `../../../etc/passwd` o simili) collassa
sempre su `"it"` o `"en"`, nessuna possibilità di iniettare un path.

### 2.3 Logo PDF — asset sviluppatore, mai admin/utente-caricato

`billing-service/.../PdfInvoiceServiceImpl.java:51-56,138`:

```java
// Classpath location for the dev-provided hotel logo (never admin-uploaded —
// see ADR in backup/DECISIONS.md: uploads are an attack surface, a static asset
// shipped by the developer is not).
private static final String LOGO_CLASSPATH_RESOURCE = "static/pdf/logo.png";
...
try (InputStream in = getClass().getClassLoader().getResourceAsStream(LOGO_CLASSPATH_RESOURCE)) {
```

Percorso costante, nessuna variabile — commento nel codice conferma esplicitamente
che si tratta di una decisione architetturale deliberata (niente upload loghi per
evitare la superficie d'attacco). Nessun finding.

### 2.4 Report Alloggiati (TXT/JSON) — generati in memoria, nessun file temporaneo

`frontdesk-service/.../stays/controller/StayController.java:160-199`:

```java
@GetMapping("/reports/alloggiati")
public ResponseEntity<byte[]> downloadAlloggiatiReport(
        @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
    final String content = alloggiatiReportService.generateReport(date, ...);
    final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    ...
    headers.setContentDisposition(ContentDisposition.attachment()
            .filename("alloggiati-" + date + ".txt").build());
    return ResponseEntity.ok().headers(headers).body(bytes);
}
```

- `date` è tipizzato `LocalDate` con `@DateTimeFormat(iso = ISO.DATE)`: solo
  stringhe `YYYY-MM-DD` superano il binding (stesso ragionamento del §2.1 — un
  payload di traversal non è nemmeno un `LocalDate` valido, `400` prima di
  eseguire logica applicativa).
- `AlloggiatiReportServiceImpl.generateReport`/`generateJsonReport`
  (`frontdesk-service/.../stays/service/impl/AlloggiatiReportServiceImpl.java:79-111`)
  costruiscono il report interamente con `StringBuilder` a partire da righe lette
  da `StayRepository` (query JPA parametrizzata) — **nessun file temporaneo,
  nessuna scrittura su disco, nessun `new File`/`Files.write` in tutto il metodo.**
  Nessun rischio di race condition/symlink su path condiviso: non esiste un path
  condiviso, perché non c'è alcun path.
- L'endpoint JSON (`/reports/alloggiati/json`) restituisce direttamente
  `List<AlloggiatiRowDto>` serializzato da Jackson — stesso discorso, nessun file.

### 2.5 Export batch FatturaPA (ZIP) — generato in memoria, entry-name già sanitizzato

`billing-service/.../FatturaPAServiceImpl.java:191-262`:

```java
final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
    ...
    zip.putNextEntry(new ZipEntry(zipEntryName(invoice)));
    ...
}
return buffer.toByteArray();
```

ZIP costruito in `ByteArrayOutputStream`, mai scritto su disco. Il nome di ogni
entry è prodotto da `zipEntryName` (riga 259-262):

```java
private static String zipEntryName(final InvoiceResponse invoice) {
    final String safeNumber = sanitize(invoice.invoiceNumber(), invoice.id().toString()).replace("/", "-");
    return safeNumber + ".xml";
}
```

`invoice.invoiceNumber()` è un numero di fattura **generato lato server** (schema
di numerazione sequenziale per hotel/anno, già oggetto di fix di race-condition in
sessioni precedenti secondo `00-recon.md` §8) — non testo libero immesso da un
client via API. Anche così, il codice sostituisce esplicitamente `/` con `-`
(mitigazione difensiva già presente, verosimilmente per evitare che un separatore
di directory finisca nel nome-entry dello ZIP). Non essendoci un passaggio di
*estrazione* lato server di questo ZIP (viene generato e restituito al client, mai
riletto/estratto dal backend), un eventuale zip-slip non è comunque sfruttabile
contro il server stesso — al più un rischio per chi estrae localmente lo ZIP
(fuori standard scope OWASP per un'API server-side, e comunque già mitigato dal
replace). Nessun finding azionabile.

### 2.6 Export CSV Owner Analytics — generato interamente lato client, nessun coinvolgimento del backend

`GET /api/v1/reports/owner` (`OwnerReportController.java:44-53`) restituisce
`OwnerFinancialReportDto` come **JSON**, non CSV. La conversione in CSV avviene
interamente nel browser:

`frontend/src/services/billingReportService.ts:17-47` (`exportToCsv`) costruisce
la stringa CSV da dati già in memoria (risposta JSON precedentemente ricevuta),
crea un `Blob` e lo scarica via `URL.createObjectURL` — **zero chiamate di rete
aggiuntive, zero coinvolgimento del filesystem del backend.** Non è un vettore di
path traversal lato server per definizione (nessun path costruito su nessun
backend). Nota fuori-scope: il CSV usa quoting `"..."` con escape del doppio-apice,
che mitiga CSV/formula injection sui campi testuali, ma questo è un tema per
`xss.md`/client-side-audit, non per questo documento.

### 2.7 Validatore XSD FatturaPA — schema fisso, nessun input utente

`billing-service/.../FatturaPaXsdValidator.java:39-63` — `FATTURAPA_XSD` e
`XMLDSIG_XSD` sono `private static final String` risolte via `ClassPathResource`
al costruttore (`@Component`, quindi una volta sola all'avvio, non per-richiesta).
Il metodo `validate(byte[] xml)` (righe 71-96) accetta solo il **contenuto XML
già generato dal backend** (mai un path), da validare contro lo schema
pre-compilato. Nessun input utente influenza quale file XSD viene letto.

---

## 3. `Paths.get`/risorse statiche con input non sanitizzato — verifica generale

Ricerca dedicata su tutto il repo (`Path.of(`, `.resolve(`, `getResourceAsStream(`,
`addResourceHandler`): nessun risultato che incorpori input utente. Gli unici due
usi di `getResourceAsStream` sono su costanti hard-coded (font PDF/UA in
`ThymeleafPdfTemplateRenderer.java:100-101`, logo in
`PdfInvoiceServiceImpl.java:138`). Nessuna configurazione `WebMvcConfigurer`/
`addResourceHandler` in nessun servizio che esponga una directory a percorso
variabile.

---

## Riepilogo per severità

| Area | Verdetto | Severità |
|---|---|---|
| Upload file (qualunque endpoint) | **Non esiste** — verificato con grep esaustivo su `MultipartFile`/`@RequestPart`/`multipart`/`InputStream`-param e lettura diretta di `GuestController.addIdentityDocument` (metadata-only, DTO senza campi binari) | N/A — nessun finding |
| Path traversal su `{id}` in PDF/XML/export | Non sfruttabile — `id` sempre tipizzato `UUID` (binding rifiuta payload non-UUID prima della logica applicativa), usato solo come chiave di lookup JPA, mai in un path filesystem | N/A — nessun finding |
| Selezione template Thymeleaf (PDF) | Non sfruttabile — nome template sempre costante o selezionato da un enum server-side a 2 uscite fisse | N/A — nessun finding |
| Selezione template locale (email, notification-service) | Non sfruttabile — allowlist stretta a `"it"`/`"en"`, claim di recon riconfermata sul codice attuale | N/A — nessun finding |
| Logo PDF | Asset classpath fisso, mai upload-abile (decisione architetturale documentata in-code) | N/A — nessun finding |
| Report Alloggiati TXT/JSON | Generati in memoria (`StringBuilder`/DTO), nessun file temporaneo, nessun path condiviso | N/A — nessun finding |
| Export ZIP FatturaPA batch | Generato in memoria (`ByteArrayOutputStream`); nome entry già sanitizzato (`replace("/", "-")`) e comunque derivato da dato server-generato, non input libero; nessuna estrazione lato server | N/A — nessun finding |
| Export CSV Owner Analytics | Interamente client-side (browser `Blob`), nessun coinvolgimento backend | N/A — fuori scope |
| `FatturaPaXsdValidator` | Schema XSD risolto da costanti classpath fisse, mai da input | N/A — nessun finding |
| Static resource serving custom | Non presente in nessun servizio | N/A — nessun finding |

**Conclusione complessiva: nessun finding azionabile su file-upload o path
traversal in questo codebase.** La ragione è strutturale: (1) non esiste alcun
endpoint che accetti un file come input in nessun servizio, e (2) ogni "file"
generato dal backend è prodotto interamente in memoria e servito direttamente
nella risposta HTTP, senza mai passare per un percorso filesystem costruito da
input della richiesta — gli unici path filesystem-adiacenti nel repo sono risorse
`ClassPathResource`/`getResourceAsStream` con nome costante (schema XSD, font
PDF/UA, logo, template Thymeleaf selezionati da enum o allowlist a due valori).
