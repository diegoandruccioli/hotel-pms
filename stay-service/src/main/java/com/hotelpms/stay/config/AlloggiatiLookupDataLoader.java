package com.hotelpms.stay.config;

import com.hotelpms.stay.repository.AlloggiatiComuneRepository;
import com.hotelpms.stay.repository.AlloggiatiStatoRepository;
import com.hotelpms.stay.repository.AlloggiatiTipdocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Populates Portale Alloggiati Web lookup tables at application startup
 * if they are still empty. Downloads CSV data directly from the official
 * Polizia di Stato portal and delegates parsing to {@link AlloggiatiCsvParser}.
 * Failures are non-blocking: the application continues even if downloads fail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlloggiatiLookupDataLoader implements ApplicationRunner {

    private static final int HTTP_OK = 200;
    private static final String BASE_URL =
            "https://alloggiatiweb.poliziadistato.it/portalealloggiati/ashx/Download.ashx";
    private static final String STATI_URL = BASE_URL + "?ID=1&N=STATI";
    private static final String COMUNI_URL = BASE_URL + "?ID=0&N=COMUNI";
    private static final String TIPDOC_URL = BASE_URL + "?ID=2&N=TIPDOC";

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private final AlloggiatiStatoRepository statoRepository;
    private final AlloggiatiComuneRepository comuneRepository;
    private final AlloggiatiTipdocRepository tipdocRepository;

    /** {@inheritDoc} */
    @Override
    public void run(final ApplicationArguments args) {
        loadStati();
        loadComuni();
        loadTipdoc();
    }

    // -----------------------------------------------------------------------
    // Loaders
    // -----------------------------------------------------------------------

    private void loadStati() {
        if (statoRepository.count() > 0) {
            log.info("alloggiati_stati already populated — skipping download");
            return;
        }
        try {
            final String csv = download(STATI_URL);
            final var records = AlloggiatiCsvParser.parseStati(csv);
            statoRepository.saveAll(records);
            log.info("Loaded {} stati from Portale Alloggiati", records.size());
        } catch (final IOException ex) {
            log.error("Failed to load alloggiati_stati — lookup will be empty: {}", ex.getMessage());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("alloggiati_stati download interrupted: {}", ex.getMessage());
        }
    }

    private void loadComuni() {
        if (comuneRepository.count() > 0) {
            log.info("alloggiati_comuni already populated — skipping download");
            return;
        }
        try {
            final String csv = download(COMUNI_URL);
            final var records = AlloggiatiCsvParser.parseComuni(csv);
            comuneRepository.saveAll(records);
            log.info("Loaded {} comuni from Portale Alloggiati", records.size());
        } catch (final IOException ex) {
            log.error("Failed to load alloggiati_comuni — lookup will be empty: {}", ex.getMessage());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("alloggiati_comuni download interrupted: {}", ex.getMessage());
        }
    }

    private void loadTipdoc() {
        if (tipdocRepository.count() > 0) {
            log.info("alloggiati_tipdoc already populated — skipping download");
            return;
        }
        try {
            final String csv = download(TIPDOC_URL);
            final var records = AlloggiatiCsvParser.parseTipdoc(csv);
            tipdocRepository.saveAll(records);
            log.info("Loaded {} tipdoc from Portale Alloggiati", records.size());
        } catch (final IOException ex) {
            log.error("Failed to load alloggiati_tipdoc — lookup will be empty: {}", ex.getMessage());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("alloggiati_tipdoc download interrupted: {}", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // HTTP helper
    // -----------------------------------------------------------------------

    /**
     * Downloads a URL and returns the response body as a UTF-8 string.
     *
     * @param url the URL to download
     * @return the response body
     * @throws IOException          if an I/O error occurs or the server returns non-200
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    private String download(final String url) throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();
        final HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }
}
