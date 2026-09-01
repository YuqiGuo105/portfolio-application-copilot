const DEFAULT_EXPIRY_SKEW_MS = 60_000;

export function decodeJwtPayload(token) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const normalized = parts[1].replaceAll('-', '+').replaceAll('_', '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const json = typeof atob === 'function'
      ? decodeURIComponent(Array.from(atob(padded), (character) =>
        `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''))
      : Buffer.from(padded, 'base64').toString('utf8');
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function tokenExpiresAt(token) {
  const expiration = Number(decodeJwtPayload(token)?.exp);
  return Number.isFinite(expiration) ? expiration * 1000 : 0;
}

export function isTokenUsable(token, now = Date.now(), skewMs = DEFAULT_EXPIRY_SKEW_MS) {
  const expiresAt = tokenExpiresAt(token);
  return Boolean(expiresAt && expiresAt > now + skewMs);
}

export function tokenEmail(token) {
  const email = decodeJwtPayload(token)?.email;
  return typeof email === 'string' ? email : '';
}
