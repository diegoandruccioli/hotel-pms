package com.hotelpms.commonweb.paging;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.List;

/**
 * Turns on the global stable-pagination safety net: once registered (services add
 * {@code @Import(StablePagingPostProcessor.class)} to their application class), every
 * {@code Pageable} bound by a controller gets the {@link StablePaging#CREATED_AT_TIE_BREAK}
 * appended. The library is a plain {@code java-library} (no Spring Boot auto-configuration), so
 * services opt in explicitly.
 *
 * <p>The wrapper is installed on the {@link RequestMappingHandlerAdapter} once it is fully initialised,
 * through the adapter's own {@code setArgumentResolvers}. That works whichever resolver instance
 * Spring Data registered and needs no assumption about its concrete type or configuration.
 *
 * <p>Every paginated entity must expose {@code createdAt} and {@code id}. On a derived or
 * {@code Specification} query, sorting on a missing property fails loudly with
 * {@code PropertyReferenceException}, already mapped to 400 by {@code AbstractProblemDetailAdvice}.
 * A hand-written {@code @Query} (JPQL/native) has no such check: Hibernate rejects the unknown sort
 * path at query-translation time, which surfaces as a plain 500 — worth knowing before adding this
 * tie-break to a repository method backed by {@code @Query}.
 */
public final class StablePagingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (bean instanceof RequestMappingHandlerAdapter adapter) {
            wrapPageableResolver(adapter);
        }
        return bean;
    }

    private static void wrapPageableResolver(final RequestMappingHandlerAdapter adapter) {
        final List<HandlerMethodArgumentResolver> current = adapter.getArgumentResolvers();
        if (current == null) {
            return;
        }
        adapter.setArgumentResolvers(current.stream()
                .<HandlerMethodArgumentResolver>map(resolver ->
                        resolver instanceof PageableHandlerMethodArgumentResolver
                                ? new StablePageableArgumentResolver(resolver)
                                : resolver)
                .toList());
    }
}
