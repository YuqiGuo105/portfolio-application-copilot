import assert from 'node:assert/strict';

function jwt(payload) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

const token = jwt({ exp: Math.floor(Date.now() / 1000) + 600, email: 'admin@example.com' });
const writes = [];
const removedTabs = [];
const createdTabs = [];

globalThis.chrome = {
  tabs: {
    query: async () => [],
    create: async (options) => {
      createdTabs.push(options);
      return { id: 44, ...options };
    },
    remove: async (tabId) => removedTabs.push(tabId)
  },
  scripting: {
    executeScript: async ({ target }) => {
      assert.equal(target.tabId, 44);
      return [{ result: { accessToken: token } }];
    }
  },
  storage: {
    session: {
      get: async (defaults) => ({ ...defaults }),
      set: async (value) => writes.push(value),
      remove: async () => {}
    }
  }
};

const { restoreAdminSession } = await import('../shared/admin-session.js');
const session = await restoreAdminSession();

assert.equal(session.email, 'admin@example.com');
assert.equal(session.accessToken, token);
assert.deepEqual(createdTabs, [{
  url: 'https://www.yuqi.site/admin?extension_session_sync=1',
  active: false
}]);
assert.deepEqual(removedTabs, [44]);
assert.equal(writes.length, 1);
assert.equal(writes[0].adminSession.email, 'admin@example.com');

console.log(JSON.stringify({ adminSessionTests: 'passed', backgroundSync: true }));
