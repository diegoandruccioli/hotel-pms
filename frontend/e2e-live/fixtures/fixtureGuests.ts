// The guests the live suite creates carry a recognisable e-mail: `<prefix>.<timestamp>@example.com`.
// The teardown anonymises only those, matched on the e-mail and never on the name ("Qa Round" alone
// would also match a real guest).
//
//   live.e2e.1785525795223@example.com        createGuest() in fixtures/api.ts
//   qa25.fixture.<ts> / qa25.abort.<ts> ...   qa2508 specs and bootstrap (one or more lower-case words)
//   qa2508.guest.<ts>@example.com             qa2508 guest specs
const FIXTURE_GUEST_EMAIL = /^(live\.e2e|qa25(\.[a-z]+)+|qa2508\.guest)\.\d+@example\.com$/;

export function isFixtureGuest(email: string | null | undefined): boolean {
    return typeof email === 'string' && FIXTURE_GUEST_EMAIL.test(email);
}
