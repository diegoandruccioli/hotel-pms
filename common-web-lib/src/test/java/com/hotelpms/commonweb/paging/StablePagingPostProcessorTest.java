package com.hotelpms.commonweb.paging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.PageableDefault;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Boots a real MVC + Spring Data web context (no mocks of the resolver) to prove the safety net
 * reaches a controller's {@code Pageable} parameter.
 */
class StablePagingPostProcessorTest {

    private static final String PAGES_PATH = "/pages";
    private static final String UNSORTED_PATH = "/unsorted";
    private static final String CREATED_AT_ID = "createdAt: DESC,id: ASC";
    private static final String SORT_PARAM = "sort";

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        context.register(WebConfig.class, StablePagingPostProcessor.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void appendsTieBreakToTheControllerDefaultSort() throws Exception {
        final String sort = mockMvc.perform(get(PAGES_PATH)).andReturn().getResponse().getContentAsString();

        assertThat(sort).isEqualTo("checkInDate: DESC," + CREATED_AT_ID);
    }

    @Test
    void appendsTieBreakToAClientSuppliedSort() throws Exception {
        final String sort = mockMvc.perform(get(PAGES_PATH).param(SORT_PARAM, "status,asc"))
                .andReturn().getResponse().getContentAsString();

        assertThat(sort).isEqualTo("status: ASC," + CREATED_AT_ID);
    }

    @Test
    void coversAnEndpointWithoutAnyPageableDefault() throws Exception {
        final String sort = mockMvc.perform(get(UNSORTED_PATH)).andReturn().getResponse().getContentAsString();

        assertThat(sort).isEqualTo(CREATED_AT_ID);
    }

    @Test
    void keepsPageNumberAndSizeFromTheRequest() throws Exception {
        final String body = mockMvc.perform(get(UNSORTED_PATH + "/meta").param("page", "2").param("size", "7"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("2/7");
    }

    @Test
    void doesNotDuplicateATieBreakKeyTheClientAlreadySortsOn() throws Exception {
        final String sort = mockMvc.perform(get(PAGES_PATH).param(SORT_PARAM, "id,desc"))
                .andReturn().getResponse().getContentAsString();

        assertThat(sort).isEqualTo("id: DESC," + "createdAt: DESC");
    }

    @Test
    void keepsPageNumberAndSizeWhenASortIsAlsoPresent() throws Exception {
        final String body = mockMvc.perform(get(UNSORTED_PATH + "/meta")
                        .param("page", "2").param("size", "7").param(SORT_PARAM, "status,asc"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("2/7");
    }

    @Configuration
    @EnableWebMvc
    @EnableSpringDataWebSupport
    static class WebConfig {

        @Bean
        PagedController pagedController() {
            return new PagedController();
        }
    }

    @RestController
    static class PagedController {

        @GetMapping(PAGES_PATH)
        String pages(@PageableDefault(sort = "checkInDate", direction = Sort.Direction.DESC) final Pageable pageable) {
            return pageable.getSort().toString();
        }

        @GetMapping(UNSORTED_PATH)
        String unsorted(final Pageable pageable) {
            return pageable.getSort().toString();
        }

        @GetMapping(UNSORTED_PATH + "/meta")
        String meta(final Pageable pageable) {
            return pageable.getPageNumber() + "/" + pageable.getPageSize();
        }
    }
}
