export async function connectAdminSession() {
  const tabs = await chrome.tabs.query({ url: ['https://www.yuqi.site/admin*', 'https://yuqi.site/admin*'] });
  if (!tabs.length) {
    await chrome.tabs.create({ url: 'https://www.yuqi.site/admin' });
    throw new Error('Sign in on the opened yuqi.site page, then press Connect again.');
  }

  for (const tab of tabs) {
    const [{ result }] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      world: 'MAIN',
      func: readSupabaseAccessToken
    });
    if (result) {
      await chrome.storage.session.set({ accessToken: result });
      return true;
    }
  }
  throw new Error('No active admin session found. Sign in to yuqi.site and retry.');
}

function readSupabaseAccessToken() {
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (!key || !key.startsWith('sb-') || !key.endsWith('-auth-token')) continue;
    try {
      const value = JSON.parse(localStorage.getItem(key));
      const token = value?.access_token || value?.currentSession?.access_token;
      if (typeof token === 'string' && token.split('.').length === 3) return token;
    } catch {
      // Ignore unrelated or malformed local storage values.
    }
  }
  return '';
}
