import { isTokenUsable, tokenEmail, tokenExpiresAt } from './auth-token.js';

const ADMIN_LOGIN_URL = 'https://www.yuqi.site/admin/login?redirect=%2Fadmin';
const ADMIN_SESSION_SYNC_URL = 'https://www.yuqi.site/admin?extension_session_sync=1';
const ADMIN_TAB_PATTERNS = ['https://www.yuqi.site/admin*', 'https://yuqi.site/admin*'];
const SESSION_SYNC_ATTEMPTS = 24;
const SESSION_SYNC_DELAY_MS = 250;

export async function openAdminLogin() {
  const [existing] = await chrome.tabs.query({
    url: ['https://www.yuqi.site/admin/login*', 'https://yuqi.site/admin/login*']
  });
  if (existing?.id) {
    await chrome.tabs.update(existing.id, { active: true, url: ADMIN_LOGIN_URL });
    if (existing.windowId) await chrome.windows.update(existing.windowId, { focused: true });
    return;
  }
  await chrome.tabs.create({ url: ADMIN_LOGIN_URL });
}

export async function connectAdminSession({ interactive = true, allowBackgroundSync = true } = {}) {
  const cached = await getAdminSession();
  if (cached) return cached;

  let tabs = await chrome.tabs.query({ url: ADMIN_TAB_PATTERNS });
  let temporaryTabId = null;
  if (!tabs.length && allowBackgroundSync) {
    const temporaryTab = await chrome.tabs.create({ url: ADMIN_SESSION_SYNC_URL, active: false });
    temporaryTabId = temporaryTab.id || null;
    tabs = temporaryTabId ? [temporaryTab] : [];
  }

  try {
    const session = await readSessionFromTabs(tabs, temporaryTabId ? SESSION_SYNC_ATTEMPTS : 2);
    if (session) return session;
  } finally {
    if (temporaryTabId) await chrome.tabs.remove(temporaryTabId).catch(() => {});
  }

  await clearAdminSession();
  if (interactive) await openAdminLogin();
  throw new AdminSessionError(
    interactive
      ? 'Sign in once on yuqi.site. The extension will reconnect automatically afterward.'
      : 'No saved yuqi.site admin session is available.',
    'LOGIN_REQUIRED'
  );
}

export async function restoreAdminSession() {
  try {
    return await connectAdminSession({ interactive: false, allowBackgroundSync: true });
  } catch (error) {
    if (error instanceof AdminSessionError && error.code === 'LOGIN_REQUIRED') return null;
    throw error;
  }
}

async function readSessionFromTabs(tabs, attempts) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    for (const tab of tabs) {
      const token = await readTokenFromTab(tab.id);
      if (!isTokenUsable(token)) continue;
      const session = {
        accessToken: token,
        expiresAt: tokenExpiresAt(token),
        email: tokenEmail(token),
        connectedAt: Date.now()
      };
      await chrome.storage.session.set({ adminSession: session });
      return session;
    }
    if (attempt + 1 < attempts) await delay(SESSION_SYNC_DELAY_MS);
  }
  return null;
}

async function readTokenFromTab(tabId) {
  if (!tabId) return '';
  try {
    const [{ result }] = await chrome.scripting.executeScript({
      target: { tabId },
      world: 'MAIN',
      func: readSupabaseAccessToken
    });
    return result?.accessToken || '';
  } catch {
    return '';
  }
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function readSupabaseAccessToken() {
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (!key || !key.startsWith('sb-') || !key.endsWith('-auth-token')) continue;
    try {
      const value = JSON.parse(localStorage.getItem(key));
      const token = value?.access_token || value?.currentSession?.access_token;
      if (typeof token === 'string' && token.split('.').length === 3) {
        return { accessToken: token };
      }
    } catch {
      // Ignore unrelated or malformed local storage values.
    }
  }
  return null;
}

export async function getAdminSession() {
  const stored = await chrome.storage.session.get({ adminSession: null, accessToken: '' });
  const legacyToken = stored.accessToken || '';
  const session = stored.adminSession || (legacyToken ? {
    accessToken: legacyToken,
    expiresAt: tokenExpiresAt(legacyToken),
    email: tokenEmail(legacyToken)
  } : null);
  if (!session?.accessToken) return null;
  if (!isTokenUsable(session.accessToken)) {
    await clearAdminSession();
    return null;
  }
  return session;
}

export async function clearAdminSession() {
  await chrome.storage.session.remove(['accessToken', 'adminSession']);
}

export class AdminSessionError extends Error {
  constructor(message, code = 'AUTH_REQUIRED') {
    super(message);
    this.name = 'AdminSessionError';
    this.code = code;
  }
}
