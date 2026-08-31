import { connectAdminSession } from '../shared/admin-session.js';
import { callTool, loadSettings } from '../shared/mcp-client.js';

const status = document.querySelector('#status');
const results = document.querySelector('#results');
const fieldsRoot = document.querySelector('#fields');
const applyButton = document.querySelector('#apply');
const accountRoot = document.querySelector('#account');
const pageContext = document.querySelector('#page-context');
const accountUsername = document.querySelector('#account-username');
let resolutions = [];
let currentPage = null;
let activeCredential = null;

document.querySelector('#connect').addEventListener('click', async () => {
  await run('Connected to the authenticated MCP cluster.', connectAdminSession);
});

document.querySelector('#scan').addEventListener('click', async () => {
  await run('Application fields resolved. Review before applying.', async () => {
    currentPage = await scanActivePage();
    if (!currentPage.fields.length) throw new Error('No visible application fields found on this page.');
    renderPageContext(currentPage);
    const payload = await callTool('career.resolve_application_fields', {
      applicationId: crypto.randomUUID(),
      fields: currentPage.fields
    });
    resolutions = Array.isArray(payload) ? payload : payload.fields || payload.resolutions || payload.data || [];
    renderResolutions(resolutions);
    const email = resolutions.find((item) => /email|e-mail|username/i.test(item.label) && item.value)?.value;
    if (email && !accountUsername.value) accountUsername.value = String(email);
  });
});

document.querySelector('#prepare-account').addEventListener('click', async () => {
  await run('Site account prepared. Review the page before creating it.', async () => {
    requireAccountContext();
    activeCredential = await callTool('career.prepare_site_credential', {
      origin: currentPage.origin,
      username: accountUsername.value.trim(),
      _confirmed: true
    });
    await applyCredentials(activeCredential);
  });
});

document.querySelector('#fill-login').addEventListener('click', async () => {
  await run('Stored site credential filled. Complete MFA or sign in manually.', async () => {
    requireAccountContext();
    activeCredential = await callTool('career.get_site_credential', {
      origin: currentPage.origin,
      _confirmed: true
    });
    accountUsername.value = activeCredential.username || '';
    await applyCredentials(activeCredential);
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
  return result || { pageType: 'FORM', origin: '', fields: [] };
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

async function applyCredentials(credential) {
  const tab = await activeTab();
  const [{ result }] = await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    args: [{ username: credential.username, password: credential.password }],
    func: (value) => globalThis.__yuqiApplicationCopilot?.applyCredentials(value) || { applied: [] }
  });
  if (!result?.applied?.length) throw new Error('No visible username or password fields were found.');
  return result;
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

function renderPageContext(page) {
  pageContext.hidden = false;
  document.querySelector('#page-type').textContent = page.pageType.replaceAll('_', ' ');
  document.querySelector('#page-origin').textContent = page.origin;
  accountRoot.hidden = !['SIGN_UP', 'SIGN_IN'].includes(page.pageType);
  document.querySelector('#prepare-account').hidden = page.pageType !== 'SIGN_UP';
}

function requireAccountContext() {
  if (!currentPage || !['SIGN_UP', 'SIGN_IN'].includes(currentPage.pageType)) {
    throw new Error('Scan a sign-up or sign-in page first.');
  }
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
