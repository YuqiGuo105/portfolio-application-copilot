import assert from 'node:assert/strict';
import { decodeJwtPayload, isTokenUsable, tokenEmail, tokenExpiresAt } from '../shared/auth-token.js';

function jwt(payload) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

const now = Date.UTC(2026, 7, 31, 18, 0, 0);
const valid = jwt({ exp: Math.floor((now + 10 * 60_000) / 1000), email: 'admin@example.com' });
const expired = jwt({ exp: Math.floor((now - 1_000) / 1000), email: 'admin@example.com' });

assert.equal(decodeJwtPayload(valid).email, 'admin@example.com');
assert.equal(tokenEmail(valid), 'admin@example.com');
assert.equal(tokenExpiresAt(valid), now + 10 * 60_000);
assert.equal(isTokenUsable(valid, now), true);
assert.equal(isTokenUsable(expired, now), false);
assert.equal(isTokenUsable('not-a-token', now), false);

console.log(JSON.stringify({ authTokenTests: 'passed' }));
