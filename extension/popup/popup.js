import { connectAdminSession } from '../shared/admin-session.js';
import { callTool, loadSettings } from '../shared/mcp-client.js';

const status = document.querySelector('#status');
const results = document.querySelector('#results');
const fieldsRoot = document.querySelector('#fields');
const applyButton = document.querySelector('#apply');
let resolutions = [];

document.querySelector('#connect').addEventListener('click', async () => {
  await run('Connected to the authenticated MCP cluster.', connectAdminSession);
});

document.querySelector('#scan').addEventListener('click', async () => {
  await run('Application fields resolved. Review before applying.', async () => {
    const fields = await scanActivePage();
    if (!fields.length) throw new Error('No visible application fields found on this page.');
    const payload = await callTool('career.resolve_application_fields', {
      applicationId: crypto.randomUUID(),
      fields
    });
    resolutions = Array.isArray(payload) ? payload : payload.fields || payload.resolutions || payload.data || [];
    renderResolutions(resolutions);
  });
});

applyButton.addEventListener('click', async () => {
  await run('Approved values applied. Review the application before submitting.', async () => {
    const approved = {};
    document.querySelectorAll('[data-field-id]:checked').forEach((checkbox) => {
      const item = resolutions.find((resolution) => resolution.fieldId === checkbox.dataset.fieldId);
      if (item?.status === 'RESOLVED' && item.value != null) approved[item.fieldId] = item.value;
    });
    if (!Object.keys(approved).length) throw new Error('Select at least one resolved field.');
    const applied = await applyToActivePage(approved);
    if (!applied) throw new Error('The page changed before values could be applied. Scan again.');
  });
});

loadSettings().then(({ accessToken }) => setStatus(accessToken ? 'Admin session connected' : 'Not connected'));

async function scanActivePage() {
  const tab = await activeTab();
  await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['content/form-scanner.js'] });
  const [{ result }] = await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    func: () => globalThis.__yuqiApplicationCopilot.scan()
  });
  return result || [];
}

async function applyToActivePage(values) {
  const tab = await activeTab();
  const [{ result }] = await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    args: [values],
    func: (approved) => globalThis.__yuqiApplicationCopilot?.apply(approved) || 0
  });
  return result || 0;
}

async function activeTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id || !/^https?:/.test(tab.url || '')) throw new Error('Open a job application page first.');
  return tab;
}

function renderResolutions(items) {
  results.hidden = false;
  document.querySelector('#count').textContent = `${items.length} detected`;
  fieldsRoot.replaceChildren(...items.map((item) => {
    const row = document.createElement('label');
    row.className = 'field';
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.dataset.fieldId = item.fieldId;
    checkbox.disabled = item.status !== 'RESOLVED' || item.value == null;
    checkbox.checked = !checkbox.disabled;
    checkbox.addEventListener('change', refreshApplyState);
    const text = document.createElement('span');
    const title = document.createElement('strong');
    title.textContent = item.label;
    const detail = document.createElement('small');
    detail.className = item.status === 'NEEDS_CONFIRMATION' ? 'needs-confirmation' : '';
    detail.textContent = item.status === 'RESOLVED'
      ? `${String(item.value)} · ${item.source}`
      : `${item.status.replaceAll('_', ' ')} · ${item.reason}`;
    text.append(title, detail);
    row.append(checkbox, text);
    return row;
  }));
  refreshApplyState();
}

function refreshApplyState() {
  applyButton.disabled = !document.querySelector('[data-field-id]:checked');
}

async function run(successMessage, operation) {
  setStatus('Working...');
  try {
    await operation();
    setStatus(successMessage);
  } catch (error) {
    setStatus(error.message || 'Operation failed.', true);
  }
}

function setStatus(message, error = false) {
  status.textContent = message;
  status.classList.toggle('error', error);
}
