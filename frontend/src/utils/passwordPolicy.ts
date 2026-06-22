export interface PasswordRequirement {
  key: string;
  test: (password: string) => boolean;
}

/** Mirrors the backend rule (ChangePasswordRequest.java): >=16 chars, >=2 uppercase, >=2 digits, >=2 special chars. */
export const PASSWORD_REQUIREMENTS: PasswordRequirement[] = [
  { key: 'password_req_length', test: (password) => password.length >= 16 },
  { key: 'password_req_uppercase', test: (password) => (password.match(/[A-Z]/g) ?? []).length >= 2 },
  { key: 'password_req_digits', test: (password) => (password.match(/[0-9]/g) ?? []).length >= 2 },
  { key: 'password_req_special', test: (password) => (password.match(/[^A-Za-z0-9]/g) ?? []).length >= 2 },
];

export const isPasswordValid = (password: string): boolean =>
  PASSWORD_REQUIREMENTS.every((requirement) => requirement.test(password));
