import assert from 'node:assert/strict';

function jwt(payload) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

const removedKeys = [];
const staleToken = jwt({ exp: Math.floor(Date.now() / 1000) + 600, email: 'admin@example.com', version: 'stale' });
const refreshedToken = jwt({ exp: Math.floor(Date.now() / 1000) + 1200, email: 'admin@example.com', version: 'fresh' });
let storedSession = { accessToken: staleToken };
globalThis.chrome = {
  tabs: {
    query: async () => [],
    create: async (options) => ({ id: 52, ...options }),
    remove: async () => {}
  },
  scripting: {
    executeScript: async () => [{ result: { accessToken: refreshedToken } }]
  },
  storage: {
    local: { get: async (defaults) => ({ ...defaults }) },
    session: {
      get: async (defaults) => ({ ...defaults, adminSession: storedSession }),
      set: async ({ adminSession }) => { storedSession = adminSession; },
      remove: async (keys) => {
        removedKeys.push(...keys);
        storedSession = null;
      }
    }
  }
};

let requestCount = 0;
globalThis.fetch = async (_url, options) => {
  requestCount += 1;
  if (options.headers.Authorization === `Bearer ${staleToken}`) {
    return new Response(JSON.stringify({ error: 'Invalid or expired access token' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });
  }
  return new Response(JSON.stringify({ result: { structuredContent: { status: 'ok' } } }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  });
};

const { callTool } = await import('../shared/mcp-client.js');
await callTool('career.get_candidate_profile', {});
assert.equal(requestCount, 2, 'An unauthorized MCP request should retry once after restoring a fresh session.');
assert.deepEqual(removedKeys.sort(), ['accessToken', 'adminSession']);
assert.equal(storedSession.accessToken, refreshedToken);

console.log(JSON.stringify({ mcpAuthTests: 'passed', requestCount, recovered: true }));
