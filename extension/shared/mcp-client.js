const DEFAULT_ENDPOINT = 'https://www.yuqi.site/mcp/admin';

export async function loadSettings() {
  const stored = await chrome.storage.local.get({ mcpEndpoint: DEFAULT_ENDPOINT });
  const session = await chrome.storage.session.get({ accessToken: '' });
  return { endpoint: stored.mcpEndpoint, accessToken: session.accessToken };
}

export async function callTool(name, args) {
  const { endpoint, accessToken } = await loadSettings();
  if (!accessToken) throw new Error('Connect your yuqi.site admin session first.');

  const response = await fetch(endpoint, {
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

  const text = await response.text();
  if (!response.ok) throw new Error(`MCP request failed (${response.status}).`);
  const payload = parseMcpResponse(text);
  if (payload.error) throw new Error(payload.error.message || 'MCP tool failed.');
  if (payload.result?.isError) throw new Error(readToolText(payload.result) || 'MCP tool failed.');
  return payload.result?.structuredContent || parseToolText(payload.result) || {};
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
