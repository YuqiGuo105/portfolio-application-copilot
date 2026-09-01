const HOST_NAME = 'site.yuqi.application_copilot';
const QUESTION_CACHE_PREFIX = 'question-classification:v1:';
const QUESTION_CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000;

export async function classifyWithLocalCodex({ page, fields }) {
  if (!fields.length) return { provider: 'local-codex', fields: [] };
  const cacheKey = `${QUESTION_CACHE_PREFIX}${await questionFingerprint(fields)}`;
  const cached = (await chrome.storage.local.get(cacheKey))[cacheKey];
  if (cached?.expiresAt > Date.now() && coversAllFields(cached.fields, fields)) {
    return { provider: 'local-codex-cache', fields: cached.fields };
  }
  const response = await sendNativeRequest({
    type: 'classify_fields',
    requestId: crypto.randomUUID(),
    page: {
      origin: page.origin,
      title: page.title,
      pageType: page.pageType
    },
    fields
  });
  if (!response?.ok) throw new Error(response?.error || 'Local Codex classifier is unavailable.');
  const classified = response.result?.fields || [];
  if (!coversAllFields(classified, fields)) throw new Error('Local Codex did not classify every field.');
  await chrome.storage.local.set({ [cacheKey]: { expiresAt: Date.now() + QUESTION_CACHE_TTL_MS, fields: classified } });
  return { ...(response.result || {}), provider: response.provider || 'local-codex', fields: classified };
}

async function questionFingerprint(fields) {
  const normalized = fields.map(({ label, type, options }) => ({ label, type, options: options || [] }));
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(normalized)));
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('');
}

function coversAllFields(classifications, fields) {
  const ids = new Set((classifications || []).map((item) => item.fieldId));
  return fields.length === ids.size && fields.every((field) => ids.has(field.id));
}

export async function resolveWithLocalCodex({ page, fields, profile }) {
  if (!fields.length) return [];
  const response = await sendNativeRequest({
    type: 'resolve_fields',
    requestId: crypto.randomUUID(),
    page: {
      origin: page.origin,
      title: page.title,
      pageType: page.pageType
    },
    fields,
    profile: sanitizeProfile(profile)
  });
  if (!response?.ok) throw new Error(response?.error || 'Local Codex advisor is unavailable.');
  return response.result?.fields || [];
}

function sendNativeRequest(message) {
  return new Promise((resolve, reject) => {
    const port = chrome.runtime.connectNative(HOST_NAME);
    let settled = false;
    const timeout = setTimeout(() => finish(() => reject(new Error('Local Codex request timed out.'))), 60_000);
    const finish = (callback) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      port.onMessage.removeListener(onMessage);
      port.onDisconnect.removeListener(onDisconnect);
      try { port.disconnect(); } catch { }
      callback();
    };
    const onMessage = (response) => finish(() => resolve(response));
    const onDisconnect = () => {
      const reason = chrome.runtime.lastError?.message || 'Local Codex native host disconnected.';
      finish(() => reject(new Error(reason)));
    };
    port.onMessage.addListener(onMessage);
    port.onDisconnect.addListener(onDisconnect);
    port.postMessage(message);
  });
}

function sanitizeProfile(profile = {}) {
  const pick = (item = {}) => Object.fromEntries(
    ['title', 'name', 'position', 'company', 'organization']
      .filter((key) => item[key] != null)
      .map((key) => [key, String(item[key])])
  );
  return {
    summary: String(profile.summary || ''),
    skills: Array.isArray(profile.skills) ? profile.skills.map(String) : [],
    experience: Array.isArray(profile.experience) ? profile.experience.map(pick) : [],
    projects: Array.isArray(profile.projects) ? profile.projects.map(pick) : []
  };
}
