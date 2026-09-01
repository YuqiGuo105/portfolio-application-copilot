import { AdminSessionError, clearAdminSession, restoreAdminSession } from './admin-session.js';

const DEFAULT_ENDPOINT = 'https://www.yuqi.site/mcp/admin';

export async function loadSettings() {
  const stored = await chrome.storage.local.get({ mcpEndpoint: DEFAULT_ENDPOINT });
  const session = await restoreAdminSession();
  return { endpoint: stored.mcpEndpoint, session, accessToken: session?.accessToken || '' };
}

export async function callTool(name, args) {
  const { endpoint, accessToken } = await loadSettings();
  if (!accessToken) {
    throw new AdminSessionError('Sign in to yuqi.site once, then reopen the extension. Future sessions reconnect automatically.');
  }

  let response = await requestTool(endpoint, accessToken, name, args);
  if (response.status === 401) {
    await clearAdminSession();
    const refreshed = await restoreAdminSession();
    if (refreshed?.accessToken && refreshed.accessToken !== accessToken) {
      response = await requestTool(endpoint, refreshed.accessToken, name, args);
    }
  }

  const text = await response.text();
  if (!response.ok) {
    const reason = readHttpError(text);
    if (response.status === 401) {
      await clearAdminSession();
      throw new AdminSessionError(
        'Your admin session expired or was rejected. Sign in again; automatic connection resumes afterward.',
        'SESSION_EXPIRED'
      );
    }
    if (response.status === 403) {
      throw new AdminSessionError(
        reason || 'This signed-in account does not have permission to use admin MCP tools.',
        'ACCESS_DENIED'
      );
    }
    throw new Error(reason ? `MCP request failed (${response.status}): ${reason}`
      : `MCP request failed (${response.status}).`);
  }
  const payload = parseMcpResponse(text);
  if (payload.error) throw new Error(payload.error.message || 'MCP tool failed.');
  if (payload.result?.isError) throw new Error(readToolText(payload.result) || 'MCP tool failed.');
  return payload.result?.structuredContent || parseToolText(payload.result) || {};
}

function requestTool(endpoint, accessToken, name, args) {
  return fetch(endpoint, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json, text/event-stream',
      'Content-Type': 'application/json',
      'MCP-Protocol-Version': '2025-03-26'
    },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: crypto.randomUUID(),
      method: 'tools/call',
      params: { name, arguments: args }
    })
  });
}

function readHttpError(text) {
  try {
    const payload = JSON.parse(text);
    const message = payload?.error?.message || payload?.error || payload?.message;
    return typeof message === 'string' ? message : '';
  } catch {
    return '';
  }
}

function parseMcpResponse(text) {
  if (text.trim().startsWith('{')) return JSON.parse(text);
  const data = text.split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
    .filter(Boolean)
    .at(-1);
  if (!data) throw new Error('MCP returned an empty response.');
  return JSON.parse(data);
}

function readToolText(result) {
  return result?.content?.find((item) => item.type === 'text')?.text || '';
}

function parseToolText(result) {
  const text = readToolText(result);
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { text }; }
}
