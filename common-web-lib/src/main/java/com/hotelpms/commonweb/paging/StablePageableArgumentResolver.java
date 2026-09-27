package com.hotelpms.commonweb.paging;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Wraps Spring Data's {@code Pageable} resolver and applies {@link StablePaging}'s
 * {@code createdAt desc, id asc} tie-break to every {@link Pageable} a controller receives, whatever
 * {@code ?sort=} the client sent and whether or not the endpoint declares a {@code @PageableDefault}.
 * Every paginated controller is covered without touching it, including ones added later.
 */
public final class StablePageableArgumentResolver implements HandlerMethodArgumentResolver {

    private final HandlerMethodArgumentResolver delegate;

    /**
     * Creates the wrapper.
     *
     * @param delegate the resolver that actually builds the {@link Pageable} from the request
     */
    public StablePageableArgumentResolver(final HandlerMethodArgumentResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return delegate.supportsParameter(parameter);
    }

    @Override
    public Object resolveArgument(final MethodParameter parameter, final ModelAndViewContainer mavContainer,
            final NativeWebRequest webRequest, final WebDataBinderFactory binderFactory) throws Exception {
        final Object resolved = delegate.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        return resolved instanceof Pageable pageable ? StablePaging.withCreatedAtTieBreak(pageable) : resolved;
    }
}
