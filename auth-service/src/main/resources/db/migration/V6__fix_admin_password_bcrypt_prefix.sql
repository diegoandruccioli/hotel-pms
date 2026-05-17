-- Fix: DelegatingPasswordEncoder requires {bcrypt} prefix.
-- V1 seed inserted a raw BCrypt hash without the prefix,
-- causing login to fail on a fresh deployment.
-- The NOT LIKE guard makes this idempotent: running twice
-- does not double-prefix the hash.
UPDATE user_account
SET password_hash = '{bcrypt}' || password_hash
WHERE id = '00000000-0000-0000-0000-000000000000'
  AND password_hash NOT LIKE '{%}%';
