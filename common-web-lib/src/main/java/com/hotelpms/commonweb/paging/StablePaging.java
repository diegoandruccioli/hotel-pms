package com.hotelpms.commonweb.paging;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Makes paginated queries deterministic. Sorting on a non-unique column (a check-in date, a last
 * name, an issue date) leaves rows with equal keys in an undefined order, and PostgreSQL does not
 * keep that order stable between two {@code LIMIT/OFFSET} requests: a row can then be skipped or
 * shown twice while the user pages through the list. Appending a unique final key makes the order
 * total, so every page is a consistent slice of the same sequence.
 *
 * <p>The tie-break is appended after whatever the caller asked for, never before it, and only for
 * keys the caller is not already sorting on. Unpaged requests are returned unchanged.
 *
 * <p>The rebuilt {@link Pageable} is a plain {@link PageRequest} (page number and size are kept).
 * That is what Spring Data's argument resolver and every service in this codebase produces.
 */
public final class StablePaging {

    /** Unique primary key, ascending — the minimum needed to make any sort total. */
    public static final Sort ID_TIE_BREAK = Sort.by(Sort.Order.asc("id"));

    /**
     * Newest-created first, then primary key. Preferred for entities with {@code createdAt}: among
     * rows that tie on the requested sort key, the most recent one is listed first.
     */
    public static final Sort CREATED_AT_TIE_BREAK =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private StablePaging() {
    }

    /**
     * Appends {@code tieBreak} to the sort of {@code pageable}, skipping properties it already sorts on.
     *
     * @param pageable the requested page, may be {@code null} (treated as unpaged)
     * @param tieBreak the keys to append; the last one must be unique for the order to be total
     * @return the pageable with the tie-break applied, or {@code pageable} itself when unpaged
     */
    public static Pageable withTieBreak(final Pageable pageable, final Sort tieBreak) {
        if (pageable == null) {
            return Pageable.unpaged();
        }
        if (pageable.isUnpaged()) {
            return pageable;
        }
        final Sort requested = pageable.getSort();
        final Sort missing = Sort.by(tieBreak.stream()
                .filter(order -> requested.getOrderFor(order.getProperty()) == null)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), requested.and(missing));
    }

    /**
     * Applies {@link #CREATED_AT_TIE_BREAK}.
     *
     * @param pageable the requested page
     * @return the pageable ending with {@code createdAt desc, id asc}
     */
    public static Pageable withCreatedAtTieBreak(final Pageable pageable) {
        return withTieBreak(pageable, CREATED_AT_TIE_BREAK);
    }

    /**
     * Applies {@link #ID_TIE_BREAK}, for entities that have no {@code createdAt}.
     *
     * @param pageable the requested page
     * @return the pageable ending with {@code id asc}
     */
    public static Pageable withIdTieBreak(final Pageable pageable) {
        return withTieBreak(pageable, ID_TIE_BREAK);
    }
}
