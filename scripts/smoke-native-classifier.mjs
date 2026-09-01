import { spawn } from 'node:child_process';
import { once } from 'node:events';

const request = {
  type: 'classify_fields',
  requestId: crypto.randomUUID(),
  page: { origin: 'https://ats.example', title: 'Application', pageType: 'APPLICATION' },
  fields: [
    { id: 'gender', label: 'Gender', type: 'combobox', options: ['Male', 'Female', 'Choose not to disclose'] },
    { id: 'race', label: 'Please identify your race', type: 'combobox', options: ['Asian', 'White'] },
    { id: 'hispanic', label: 'Are you Hispanic/Latino?', type: 'combobox', options: ['Yes', 'No'] }
  ]
};

const hostPath = process.env.YUQI_NATIVE_HOST_PATH || new URL('./run-native-host.sh', import.meta.url).pathname;
const host = spawn(hostPath, [], { stdio: ['pipe', 'pipe', 'inherit'] });
const payload = Buffer.from(JSON.stringify(request));
const header = Buffer.alloc(4);
header.writeUInt32LE(payload.length);
host.stdin.end(Buffer.concat([header, payload]));

const chunks = [];
host.stdout.on('data', (chunk) => chunks.push(chunk));
await Promise.race([
  once(host, 'close'),
  new Promise((_, reject) => setTimeout(() => reject(new Error('Native classifier smoke test timed out')), 45_000))
]);
const response = Buffer.concat(chunks);
if (response.length < 4) throw new Error('Native classifier returned no framed response');
const size = response.readUInt32LE(0);
const result = JSON.parse(response.subarray(4, 4 + size).toString('utf8'));
if (!result.ok) throw new Error(result.error || 'Native classifier failed');
if (result.provider !== 'local-codex') throw new Error(`Unexpected provider: ${result.provider}`);
if (result.result?.fields?.length !== request.fields.length) throw new Error('Classifier did not cover every field');
if (result.result.fields.some((field) => Object.hasOwn(field, 'value'))) throw new Error('Classifier returned candidate answers');
console.log(JSON.stringify({ provider: result.provider,
  fields: result.result.fields.map(({ fieldId, semanticKey, category, status }) => ({ fieldId, semanticKey, category, status })) }));
