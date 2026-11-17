package com.hotelpms.commonweb.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonNamesTest {

    private static final String FIRST = "Mario";
    private static final String LAST = "Rossi";
    private static final String COMPOUND_LAST = "Suite Guest";

    @Test
    void putsTheSurnameBeforeTheFirstName() {
        assertThat(PersonNames.surnameFirst(FIRST, LAST)).isEqualTo("Rossi Mario");
    }

    @Test
    void keepsASurnameMadeOfSeveralWordsTogether() {
        assertThat(PersonNames.surnameFirst("Live", COMPOUND_LAST)).isEqualTo("Suite Guest Live");
    }

    @Test
    void trimsStraySpaces() {
        assertThat(PersonNames.surnameFirst("  " + FIRST + " ", " " + LAST + "  ")).isEqualTo("Rossi Mario");
    }

    @Test
    void returnsWhateverIsPresentWhenOnePartIsMissing() {
        assertThat(PersonNames.surnameFirst(null, LAST)).isEqualTo(LAST);
        assertThat(PersonNames.surnameFirst(FIRST, " ")).isEqualTo(FIRST);
    }

    @Test
    void returnsAnEmptyStringWhenBothPartsAreMissing() {
        assertThat(PersonNames.surnameFirst(null, null)).isEmpty();
    }
}
