import assert from 'node:assert/strict';

const storage = new Map();
let connectionCount = 0;
let postedMessage;

function listenerSet() {
  const listeners = new Set();
  return {
    addListener: (listener) => listeners.add(listener),
    removeListener: (listener) => listeners.delete(listener),
    emit: (value) => [...listeners].forEach((listener) => listener(value))
  };
}

globalThis.chrome = {
  runtime: {
    connectNative: () => {
      connectionCount += 1;
      const onMessage = listenerSet();
      const onDisconnect = listenerSet();
      return {
        onMessage,
        onDisconnect,
        postMessage(message) {
          postedMessage = message;
          queueMicrotask(() => onMessage.emit({
            ok: true,
            provider: 'local-codex',
            result: { fields: message.fields.map((field) => ({
              fieldId: field.id,
              semanticKey: field.id,
              category: 'OTHER',
              status: 'CLASSIFIED',
              confidence: 0.99,
              reason: 'Visible label'
            })) }
          }));
        },
        disconnect() { onDisconnect.emit(); }
      };
    }
  },
  storage: {
    local: {
      async get(key) { return storage.has(key) ? { [key]: storage.get(key) } : {}; },
      async set(values) { Object.entries(values).forEach(([key, value]) => storage.set(key, value)); }
    }
  }
};

const { classifyWithLocalCodex } = await import('../shared/codex-client.js');
const page = { origin: 'https://ats.example', title: 'Application', pageType: 'APPLICATION' };
const fields = [{ id: 'gender', label: 'Gender', type: 'combobox', options: ['Male', 'Female'] }];

const first = await classifyWithLocalCodex({ page, fields });
assert.equal(first.provider, 'local-codex');
assert.equal(first.fields[0].semanticKey, 'gender');
assert.equal(connectionCount, 1);
assert.equal(postedMessage.type, 'classify_fields');
assert.equal(Object.hasOwn(postedMessage, 'profile'), false, 'Question classification must not send candidate data.');

const cached = await classifyWithLocalCodex({ page, fields });
assert.equal(cached.provider, 'local-codex-cache');
assert.equal(connectionCount, 1, 'A repeated form should use the local 30-day classification cache.');

console.log(JSON.stringify({ codexClientTests: 'passed', persistentPort: true, cacheHit: true }));
