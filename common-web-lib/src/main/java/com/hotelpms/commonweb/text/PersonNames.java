package com.hotelpms.commonweb.text;

/**
 * Shared composition of a person's display name for lists and screens.
 *
 * <p>Italian front-desk registers (and the Alloggiati reports) read "Cognome Nome", and
 * the stays already store the primary guest that way; every list that shows a guest
 * uses this one rule so the same guest reads the same everywhere. Documents and
 * messages addressed to the guest (PDFs, emails) keep the natural "Nome Cognome" and do
 * not use this class.
 */
public final class PersonNames {

    private PersonNames() {
    }

    /**
     * Joins the surname and the first name, surname first.
     *
     * @param firstName the first name, may be {@code null}
     * @param lastName  the surname, may be {@code null}
     * @return "Cognome Nome" without stray spaces, or an empty string when both are blank
     */
    public static String surnameFirst(final String firstName, final String lastName) {
        final String last = lastName == null ? "" : lastName.trim();
        final String first = firstName == null ? "" : firstName.trim();
        return (last + " " + first).trim();
    }
}
