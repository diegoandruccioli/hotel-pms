interface PersonNameParts {
  firstName?: string | null;
  lastName?: string | null;
}

/** "Cognome Nome": the order the stays, the reservations and the invoices already use, and
 * the one the guest list is sorted by. Documents addressed to the guest keep "Nome Cognome". */
export const formatGuestName = ({ firstName, lastName }: PersonNameParts): string =>
  `${lastName ?? ''} ${firstName ?? ''}`.trim();
