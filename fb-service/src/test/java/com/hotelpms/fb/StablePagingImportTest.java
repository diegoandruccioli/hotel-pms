package com.hotelpms.fb;

import com.hotelpms.commonweb.paging.StablePagingPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the global stable-pagination safety net (see {@link StablePagingPostProcessor}):
 * every service with paginated endpoints must {@code @Import} it, and nothing catches a
 * removed import except this test — losing it silently brings back unstable
 * {@code LIMIT/OFFSET} ordering on every paginated listing in this service.
 */
class StablePagingImportTest {

    @Test
    void applicationClassImportsTheStablePagingSafetyNet() {
        final Import imported = FbApplication.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(imported.value()).contains(StablePagingPostProcessor.class);
    }
}
