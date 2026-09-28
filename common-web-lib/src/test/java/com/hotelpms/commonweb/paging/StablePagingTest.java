package com.hotelpms.commonweb.paging;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class StablePagingTest {

    private static final String CHECK_IN_DATE = "checkInDate";
    private static final String CREATED_AT = "createdAt";
    private static final String ID = "id";
    private static final String STATUS = "status";
    private static final int PAGE_SIZE = 20;
    private static final int PAGE_NUMBER = 3;

    @Test
    void appendsCreatedAtThenIdAfterTheRequestedSort() {
        final Pageable result = StablePaging.withCreatedAtTieBreak(
                PageRequest.of(0, PAGE_SIZE, Sort.by(CHECK_IN_DATE).descending()));

        assertThat(result.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc(CHECK_IN_DATE), Sort.Order.desc(CREATED_AT), Sort.Order.asc(ID)));
    }

    @Test
    void keepsPageNumberAndSize() {
        final Pageable result = StablePaging.withIdTieBreak(PageRequest.of(PAGE_NUMBER, PAGE_SIZE));

        assertThat(result.getPageNumber()).isEqualTo(PAGE_NUMBER);
        assertThat(result.getPageSize()).isEqualTo(PAGE_SIZE);
        assertThat(result.getOffset()).isEqualTo((long) PAGE_NUMBER * PAGE_SIZE);
    }

    @Test
    void addsTieBreakToAnUnsortedRequest() {
        final Pageable result = StablePaging.withCreatedAtTieBreak(PageRequest.of(0, PAGE_SIZE));

        assertThat(result.getSort()).isEqualTo(StablePaging.CREATED_AT_TIE_BREAK);
    }

    @Test
    void doesNotDuplicateAKeyTheCallerAlreadySortsOn() {
        final Pageable result = StablePaging.withCreatedAtTieBreak(
                PageRequest.of(0, PAGE_SIZE, Sort.by(CREATED_AT).ascending()));

        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Order.asc(CREATED_AT), Sort.Order.asc(ID)));
    }

    @Test
    void doesNothingWhenTheCallerAlreadySortsOnEveryTieBreakKey() {
        final Sort full = Sort.by(Sort.Order.asc(ID));

        final Pageable result = StablePaging.withIdTieBreak(PageRequest.of(0, PAGE_SIZE, full));

        assertThat(result.getSort()).isEqualTo(full);
    }

    @Test
    void isIdempotent() {
        final Pageable once = StablePaging.withCreatedAtTieBreak(
                PageRequest.of(0, PAGE_SIZE, Sort.by("lastName").ascending()));

        assertThat(StablePaging.withCreatedAtTieBreak(once).getSort()).isEqualTo(once.getSort());
    }

    @Test
    void keepsTheDirectionOfTheRequestedSort() {
        final Pageable result = StablePaging.withIdTieBreak(
                PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Order.desc(STATUS))));

        assertThat(result.getSort().getOrderFor(STATUS)).isEqualTo(Sort.Order.desc(STATUS));
        assertThat(result.getSort().getOrderFor(ID)).isEqualTo(Sort.Order.asc(ID));
    }

    @Test
    void returnsUnpagedRequestsUnchanged() {
        final Pageable unpaged = Pageable.unpaged();

        assertThat(StablePaging.withCreatedAtTieBreak(unpaged)).isSameAs(unpaged);
    }

    @Test
    void treatsNullAsUnpaged() {
        assertThat(StablePaging.withIdTieBreak(null).isUnpaged()).isTrue();
    }
}
