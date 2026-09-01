import { AdminSessionError, connectAdminSession, openAdminLogin, restoreAdminSession } from '../shared/admin-session.js';
import { calculateApplicationProgress } from '../shared/application-progress.js';
import { classifyWithLocalCodex, resolveWithLocalCodex } from '../shared/codex-client.js';
import { callTool } from '../shared/mcp-client.js';

const status = document.querySelector('#status');
const results = document.querySelector('#results');
const fieldsRoot = document.querySelector('#fields');
const applyButton = document.querySelector('#apply');
const accountRoot = document.querySelector('#account');
const pageContext = document.querySelector('#page-context');
const accountUsername = document.querySelector('#account-username');
const resumeUpload = document.querySelector('#resume-upload');
const resumeFile = document.querySelector('#resume-file');
const reviewSummary = document.querySelector('#review-summary');
const connectionState = document.querySelector('#connection-state');
let resolutions = [];
let currentPage = null;
let activeCredential = null;
let applicationId = null;
let activeResumeAsset = null;
let resumeAssetWarning = '';
let managedResumeAttached = false;
const signInButton = document.querySelector('#sign-in');
const scanButton = document.querySelector('#scan');
let operationRunning = false;
let connectionChecking = false;

signInButton.addEventListener('click', async () => {
  await run('Finish signing in on yuqi.site, then reopen this panel. Connection is automatic.', openAdminLogin);
});

scanButton.addEventListener('click', async () => {
  await run(null, async () => {
    const session = await connectAdminSession({ interactive: true, allowBackgroundSync: true });
    setConnectionState(true, session.email);
    setWorkflow('scan');
    currentPage = await scanActivePage();
    if (currentPage.outcome?.kind === 'SUBMITTED') {
      const active = await loadActiveWorkflow(currentPage.origin);
      if (!active?.applicationId) throw new Error('Submission success detected, but no active application workflow was found.');
      const workflow = await callTool('career.record_submission_receipt', {
        applicationId: active.applicationId,
        successUrl: currentPage.url,
        confirmationText: currentPage.outcome.confirmationText
      });
      setWorkflow('ready');
      setStatus(`Submission receipt recorded as ${workflow.state || 'SUBMITTED'}. External email confirmation remains separate.`);
      return;
    }
    if (!currentPage.fields.length && !currentPage.files.length) throw new Error('No visible application fields found on this page.');
    applicationId = crypto.randomUUID();
    await saveActiveWorkflow(currentPage.origin, applicationId);
    setStatus('Normalizing application questions with local Codex...');
    const classification = await classifyApplicationQuestions(currentPage, applicationId);
    currentPage.fields = applyQuestionClassifications(currentPage.fields, classification.fields);
    currentPage.questionModel = classification.provider;
    renderPageContext(currentPage);
    await callTool('career.start_application_workflow', {
      applicationId,
      ats: currentPage.adapter,
      origin: currentPage.origin,
      pageUrl: currentPage.url,
      jobTitle: currentPage.title,
      detectedFields: currentPage.fields.length + currentPage.files.length
    });
    setWorkflow('resolve');
    setStatus('Resolving known fields through the policy-aware Career service...');
    const [payload, profile, managedResume] = await Promise.all([
      callTool('career.resolve_application_fields', {
        applicationId,
        fields: currentPage.fields
      }),
      callTool('career.get_candidate_profile', {}),
      loadActiveResumeAsset()
    ]);
    activeResumeAsset = managedResume;
    managedResumeAttached = false;
    renderManagedResume(activeResumeAsset);
    resolutions = normalizeResolutions(payload);

    const unresolved = new Set(resolutions.filter((item) => item.status === 'UNSUPPORTED').map((item) => item.fieldId));
    const unresolvedFields = currentPage.fields.filter((field) => unresolved.has(field.id));
    let codexAvailable = true;
    if (unresolvedFields.length) {
      setStatus(`Asking local Codex for ${unresolvedFields.length} non-sensitive field suggestion(s)...`);
      try {
        const advice = await resolveWithLocalCodex({ page: currentPage, fields: unresolvedFields, profile });
        resolutions = mergeCodexAdvice(resolutions, advice);
      } catch (error) {
        codexAvailable = false;
        console.warn('Local Codex advisor unavailable', error);
      }
    }
    renderResolutions(resolutions);
    await callTool('career.record_application_resolution', {
      applicationId,
      resolved: resolutions.filter((item) => item.status === 'RESOLVED').length,
      requiresReview: resolutions.filter((item) => item.status === 'NEEDS_CONFIRMATION').length,
      unsupported: resolutions.filter((item) => item.status === 'UNSUPPORTED').length
    });
    const email = resolutions.find((item) => /email|e-mail|username/i.test(item.label) && item.value)?.value;
    if (email && !accountUsername.value) accountUsername.value = String(email);
    setWorkflow('review');
    const resolvedCount = resolutions.filter((item) => item.status === 'RESOLVED' && item.value != null).length;
    const reviewCount = resolutions.filter((item) => item.status === 'NEEDS_CONFIRMATION' && item.value != null).length;
    const modelStatus = `Question model: ${currentPage.questionModel || 'raw fallback'}.`;
    const resolutionStatus = codexAvailable
      ? `${resolvedCount} verified value(s) ready and ${reviewCount} sensitive value(s) await confirmation. Local Codex suggestions remain unchecked.`
      : `${resolvedCount} verified value(s) ready and ${reviewCount} sensitive value(s) await confirmation. Local Codex is unavailable; unresolved fields were left blank.`;
    const deterministic = Object.fromEntries(resolutions
      .filter((item) => item.status === 'RESOLVED' && item.value != null)
      .map((item) => [item.fieldId, item.value]));
    const autoApplied = Object.keys(deterministic).length ? await applyToActivePage(deterministic) : 0;
    const resumeField = managedResumeField();
    if (activeResumeAsset && resumeField) {
      const selectedResume = await downloadManagedResume();
      await applyResumeToActivePage(resumeField.id, selectedResume);
      managedResumeAttached = true;
      if (reviewCount) {
        setWorkflow('review');
        applyButton.disabled = false;
        applyButton.textContent = `Apply ${reviewCount} reviewed answer${reviewCount === 1 ? '' : 's'}`;
        setStatus(`${autoApplied} verified field(s) filled and the managed resume attached. Question model: ${currentPage.questionModel || 'raw fallback'}. Confirm the ${reviewCount} preselected sensitive answer${reviewCount === 1 ? '' : 's'}, then apply them once.`);
      } else {
        await recordCompletedFill(autoApplied, true);
        applyButton.disabled = true;
        applyButton.textContent = 'Safe fields applied';
        setStatus(`${autoApplied} deterministic field(s) filled and the managed resume attached. Question model: ${currentPage.questionModel || 'raw fallback'}. Review the page; final submission stays manual.`);
      }
    } else {
      setWorkflow('review');
      const resumeStatus = resumeField
        ? resumeAssetWarning || 'Choose a local PDF below, then use Apply selected to attach it.' : '';
      setStatus(`${autoApplied} deterministic field(s) filled automatically. ${modelStatus} ${resolutionStatus} ${resumeStatus}`.trim(), Boolean(resumeAssetWarning));
    }
  });
});

async function classifyApplicationQuestions(page, currentApplicationId) {
  try {
    const local = await classifyWithLocalCodex({ page, fields: page.fields });
    return { provider: local.provider || 'local Codex', fields: local.fields || [] };
  } catch (localError) {
    console.warn('Local Codex question classifier unavailable', localError);
  }
  try {
    const remote = await callTool('career.classify_application_fields', {
      applicationId: currentApplicationId,
      fields: page.fields
    });
    return { provider: remote.provider || 'Gemini fallback', fields: remote.fields || [] };
  } catch (remoteError) {
    console.warn('Gemini question classifier unavailable', remoteError);
    return { provider: 'raw-label fallback', fields: [] };
  }
}

function applyQuestionClassifications(fields, classifications) {
  const byId = new Map((classifications || []).map((item) => [item.fieldId, item]));
  return fields.map((field) => {
    const classification = byId.get(field.id);
    if (!classification || classification.status !== 'CLASSIFIED' || Number(classification.confidence) < 0.7 ||
        !classification.semanticKey) return field;
    return { ...field, semanticKey: classification.semanticKey,
      questionClassification: { provider: classification.provider || '', confidence: classification.confidence,
        category: classification.category || 'OTHER' } };
  });
}

document.querySelector('#prepare-account').addEventListener('click', async () => {
  await run('Site account prepared. Review the page before creating it.', async () => {
    requireAccountContext();
    activeCredential = await callTool('career.prepare_site_credential', {
      origin: currentPage.origin, username: accountUsername.value.trim(), _confirmed: true
    });
    await applyCredentials(activeCredential);
  });
});

document.querySelector('#fill-login').addEventListener('click', async () => {
  await run('Stored site credential filled. Complete MFA or sign in manually.', async () => {
    requireAccountContext();
    activeCredential = await callTool('career.get_site_credential', { origin: currentPage.origin, _confirmed: true });
    accountUsername.value = activeCredential.username || '';
    await applyCredentials(activeCredential);
  });
});

applyButton.addEventListener('click', async () => {
  await run(null, async () => {
    setWorkflow('fill');
    const approved = {};
    document.querySelectorAll('[data-field-id]:checked').forEach((checkbox) => {
      const item = resolutions.find((resolution) => resolution.fieldId === checkbox.dataset.fieldId);
      if (item?.value != null) approved[item.fieldId] = item.value;
    });
    const localResume = resumeFile.files?.[0];
    const hasResume = Boolean(currentPage.files.length && (localResume || activeResumeAsset));
    if (!Object.keys(approved).length && !hasResume) throw new Error('Select at least one reviewed field or configure an active resume.');
    if (!applicationId) throw new Error('The application workflow expired. Scan the page again.');
    await callTool('career.record_application_review', {
      applicationId, approvedFields: Object.keys(approved).length
    });
    const applied = Object.keys(approved).length ? await applyToActivePage(approved) : 0;
    if (Object.keys(approved).length && !applied) throw new Error('The page changed before values could be applied. Scan again.');
    const resumeField = managedResumeField();
    const selectedResume = resumeField && !managedResumeAttached
      ? localResume || (activeResumeAsset ? await downloadManagedResume() : null)
      : null;
    if (selectedResume) await applyResumeToActivePage(resumeField.id, selectedResume);
    managedResumeAttached = managedResumeAttached || Boolean(selectedResume);
    await recordCompletedFill(applied, managedResumeAttached, false);
    const action = currentPage.action?.kind === 'FINAL_SUBMIT' ? `Final action detected: “${currentPage.action.text}”.`
      : currentPage.action?.kind === 'CONTINUE' ? `Next step detected: “${currentPage.action.text}”.` : '';
    const resumeStatus = managedResumeAttached ? ' and resume attached'
      : currentPage.files.length ? '. Resume was not attached; choose a local PDF or restore the managed Resume Vault' : '';
    setStatus(`${applied} field(s) filled${resumeStatus}. ${action} Review the page; submission remains manual.`,
      Boolean(currentPage.files.length && !selectedResume));
  });
});

initializeSession();

async function initializeSession() {
  setConnectionState('checking');
  try {
    const session = await restoreAdminSession();
    setConnectionState(Boolean(session), session?.email);
    setStatus(session
      ? `Ready${session.email ? ` as ${session.email}` : ''}. Open an application and start auto-fill.`
      : 'Sign in once to enable private resume-backed auto-fill.');
  } catch (error) {
    setConnectionState(false);
    setStatus(error.message || 'Unable to restore the saved session.', true);
  }
}

async function scanActivePage() {
  const tab = await activeTab();
  await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['content/form-scanner.js'] });
  const [{ result }] = await chrome.scripting.executeScript({ target: { tabId: tab.id },
    func: () => globalThis.__yuqiApplicationCopilot.scan() });
  const page = result || { pageType: 'FORM', origin: '', fields: [], files: [], action: { kind: 'NONE' } };
  page.faviconUrl = chrome.runtime.getURL(`/_favicon/?pageUrl=${encodeURIComponent(tab.url)}&size=64`);
  return page;
}

async function applyToActivePage(values) {
  const tab = await activeTab();
  const instructions = Object.entries(values).map(([id, value]) => {
    const field = currentPage?.fields?.find((item) => item.id === id) || {};
    return { id, value, label: field.label || '', semanticKey: field.semanticKey || '' };
  });
  const [{ result }] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, args: [instructions],
    func: async (approved) => await globalThis.__yuqiApplicationCopilot?.apply(approved) || 0 });
  return result || 0;
}

async function applyResumeToActivePage(fieldId, file) {
  if (file.size > 10 * 1024 * 1024) throw new Error('Resume files must be 10 MB or smaller.');
  const tab = await activeTab();
  const payload = { name: file.name, type: file.type || 'application/pdf', dataUrl: await readAsDataUrl(file) };
  const [{ result }] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, args: [fieldId, payload],
    func: async (id, value) => globalThis.__yuqiApplicationCopilot?.applyFile(id, value) });
  if (!result?.name) throw new Error('The ATS did not accept the selected resume.');
  return result;
}

async function loadActiveResumeAsset() {
  resumeAssetWarning = '';
  try {
    return await callTool('career.get_active_resume_asset', {});
  } catch (error) {
    const message = error.message || '';
    if (/no active managed resume|no active resume/i.test(message)) return null;
    if (/404|not found/i.test(message)) {
      resumeAssetWarning = 'Managed Resume Vault is unavailable on the current backend revision; use a local PDF until deployment is repaired.';
      return null;
    }
    throw error;
  }
}

async function downloadManagedResume() {
  setStatus('Fetching the active resume through a short-lived private download ticket...');
  const ticket = await callTool('career.get_active_resume_download', { _confirmed: true });
  const response = await fetch(ticket.downloadUrl, { cache: 'no-store', credentials: 'omit' });
  if (!response.ok) throw new Error(`Managed resume download failed (${response.status}).`);
  const bytes = await response.arrayBuffer();
  if (bytes.byteLength !== Number(ticket.sizeBytes)) throw new Error('Managed resume size verification failed.');
  const actualHash = await sha256Hex(bytes);
  if (ticket.sha256 && actualHash !== String(ticket.sha256).toLowerCase()) {
    throw new Error('Managed resume integrity verification failed.');
  }
  return new File([bytes], ticket.fileName, { type: ticket.mimeType || 'application/pdf' });
}

function managedResumeField() {
  if (!currentPage?.files?.length) return null;
  return currentPage.files.find((field) => field.semanticKey === 'resume') || currentPage.files[0];
}

async function recordCompletedFill(applied, resumeAttached, recordReview = true) {
  if (recordReview) {
    await callTool('career.record_application_review', { applicationId, approvedFields: applied });
  }
  await callTool('career.record_application_fill', {
    applicationId,
    appliedFields: applied,
    resumeAttached,
    detectedAction: currentPage.action?.kind || 'NONE'
  });
  setWorkflow('ready');
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('');
}

async function applyCredentials(credential) {
  const tab = await activeTab();
  const [{ result }] = await chrome.scripting.executeScript({ target: { tabId: tab.id },
    args: [{ username: credential.username, password: credential.password }],
    func: (value) => globalThis.__yuqiApplicationCopilot?.applyCredentials(value) || { applied: [] } });
  if (!result?.applied?.length) throw new Error('No visible username or password fields were found.');
  return result;
}

async function activeTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id || !/^https?:/.test(tab.url || '')) throw new Error('Open a job application page first.');
  return tab;
}

async function saveActiveWorkflow(origin, id) {
  await chrome.storage.session.set({ activeApplicationWorkflow: { origin, applicationId: id } });
}

async function loadActiveWorkflow(origin) {
  const { activeApplicationWorkflow } = await chrome.storage.session.get({ activeApplicationWorkflow: null });
  return activeApplicationWorkflow?.origin === origin ? activeApplicationWorkflow : null;
}

function normalizeResolutions(payload) {
  const items = Array.isArray(payload) ? payload : payload.fields || payload.resolutions || payload.data || [];
  return items.map((item) => ({ ...item, fieldId: item.fieldId || item.id }));
}

function mergeCodexAdvice(items, advice) {
  const byId = new Map(advice.map((item) => [item.fieldId, item]));
  return items.map((item) => {
    const suggestion = byId.get(item.fieldId);
    if (!suggestion || suggestion.status !== 'SUGGESTION' || suggestion.value == null) return item;
    return { ...item, value: suggestion.value, status: 'NEEDS_CONFIRMATION', source: 'local Codex suggestion',
      confidence: suggestion.confidence, reason: suggestion.reason, codexSuggestion: true };
  });
}

function renderResolutions(items) {
  results.hidden = false;
  reviewSummary.hidden = false;
  const groups = groupResolutions(items);
  document.querySelector('#count').textContent = `${items.length} detected`;
  fieldsRoot.replaceChildren(...groups.map(({ label, state, items: groupItems }) => {
    const group = document.createElement('section');
    group.className = 'field-group';
    const heading = document.createElement('div');
    heading.className = 'field-group-title';
    const headingLabel = document.createElement('span');
    headingLabel.textContent = label;
    const headingCount = document.createElement('span');
    headingCount.textContent = String(groupItems.length);
    heading.append(headingLabel, headingCount);
    group.append(heading, ...groupItems.map((item) => renderResolution(item, state)));
    return group;
  }));
  renderSummary(groups, items);
  refreshApplyState();
}

function renderResolution(item, state) {
    const row = document.createElement('label');
    row.className = 'field';
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.dataset.fieldId = item.fieldId;
    checkbox.disabled = item.value == null;
    checkbox.checked = item.value != null;
    checkbox.addEventListener('change', refreshApplyState);
    const text = document.createElement('span');
    const title = document.createElement('strong');
    title.textContent = item.label;
    const detail = document.createElement('small');
    detail.className = item.codexSuggestion ? 'codex-suggestion' : item.status === 'NEEDS_CONFIRMATION' ? 'needs-confirmation' : '';
    detail.textContent = item.value != null ? `${String(item.value)} · ${item.source}`
      : `${item.status.replaceAll('_', ' ')} · ${item.reason}`;
    text.append(title, detail);
    const stateBadge = document.createElement('span');
    stateBadge.className = `field-state ${state}`;
    stateBadge.textContent = state;
    row.append(checkbox, text, stateBadge);
    return row;
}

function groupResolutions(items) {
  const ready = items.filter((item) => item.status === 'RESOLVED' && item.value != null);
  const readyIds = new Set(ready.map((item) => item.fieldId));
  const review = items.filter((item) => item.value != null && !readyIds.has(item.fieldId));
  const manual = items.filter((item) => item.value == null);
  return [
    { label: 'Ready to apply', state: 'ready', items: ready },
    { label: 'Needs your review', state: 'review', items: review },
    { label: 'Manual input required', state: 'manual', items: manual }
  ].filter((group) => group.items.length);
}

function renderSummary(groups, items) {
  const counts = Object.fromEntries(groups.map((group) => [group.state, group.items.length]));
  const ready = counts.ready || 0;
  const review = counts.review || 0;
  const manual = counts.manual || 0;
  const progress = calculateApplicationProgress({
    fields: currentPage?.fields,
    files: currentPage?.files,
    resolutions: items,
    hasResume: Boolean(activeResumeAsset || resumeFile.files?.length)
  });
  const { prepared, total, percent } = progress;
  document.querySelector('#progress-label').textContent = progress.required
    ? `${prepared} / ${total} required fields ready`
    : `${prepared} / ${total} detected fields ready`;
  document.querySelector('#progress-percent').textContent = `${percent}%`;
  document.querySelector('#progress-fill').style.width = `${percent}%`;
  document.querySelector('#readiness-percent').textContent = `${percent}%`;
  document.querySelector('#readiness-ring').style.strokeDashoffset = String(100 - percent);
  document.querySelector('#readiness-score').setAttribute('aria-label', `Application readiness ${percent} percent`);
  document.querySelector('#ready-count').textContent = String(ready);
  document.querySelector('#review-count').textContent = String(review);
  document.querySelector('#manual-count').textContent = String(manual);
}

function renderPageContext(page) {
  pageContext.hidden = false;
  document.querySelector('#page-type').textContent = `${page.adapter} · ${page.pageType.replaceAll('_', ' ')}`;
  document.querySelector('#page-origin').textContent = page.origin;
  const hostname = safeHostname(page.origin);
  const companyIcon = document.querySelector('#company-icon');
  const companyInitials = document.querySelector('#company-initials');
  companyInitials.textContent = hostname.slice(0, 2).toUpperCase() || 'AP';
  companyIcon.hidden = !page.faviconUrl;
  companyInitials.hidden = Boolean(page.faviconUrl);
  companyIcon.onerror = () => {
    companyIcon.hidden = true;
    companyInitials.hidden = false;
  };
  if (page.faviconUrl) companyIcon.src = page.faviconUrl;
  accountRoot.hidden = !['SIGN_UP', 'SIGN_IN'].includes(page.pageType);
  document.querySelector('#prepare-account').hidden = page.pageType !== 'SIGN_UP';
  resumeUpload.hidden = !page.files.length;
  document.querySelector('#resume-field-label').textContent = page.files[0]?.label || 'ATS attachment';
}

function renderManagedResume(asset) {
  const root = document.querySelector('#managed-resume');
  root.hidden = !asset;
  if (!asset) return;
  document.querySelector('#managed-resume-name').textContent = asset.displayName || 'Managed resume.pdf';
  const size = asset.sizeBytes ? `${Math.round(asset.sizeBytes / 1024)} KB` : 'Pending validation';
  const hash = asset.sha256 ? ` · ${asset.sha256.slice(0, 10)}...` : '';
  document.querySelector('#managed-resume-meta').textContent = `Private vault · ${size}${hash}`;
}

function requireAccountContext() {
  if (!currentPage || !['SIGN_UP', 'SIGN_IN'].includes(currentPage.pageType)) throw new Error('Scan a sign-up or sign-in page first.');
}

function refreshApplyState() {
  const managedResumeAvailable = Boolean(currentPage?.files?.length && activeResumeAsset);
  applyButton.disabled = !document.querySelector('[data-field-id]:checked') && !resumeFile.files?.length && !managedResumeAvailable;
}

resumeFile.addEventListener('change', () => {
  refreshApplyState();
  if (resolutions.length) renderSummary(groupResolutions(resolutions), resolutions);
});

function setWorkflow(active) {
  const order = ['scan', 'resolve', 'review', 'fill', 'ready'];
  const activeIndex = order.indexOf(active);
  document.querySelectorAll('.workflow li').forEach((item) => {
    const index = order.indexOf(item.dataset.state);
    item.classList.toggle('active', index === activeIndex);
    item.classList.toggle('complete', index < activeIndex);
  });
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('Unable to read the selected resume.'));
    reader.readAsDataURL(file);
  });
}

function safeHostname(origin) {
  try {
    return new URL(origin).hostname.replace(/^www\./, '').split('.')[0] || '';
  } catch {
    return '';
  }
}

async function run(successMessage, operation) {
  setStatus('Working...');
  operationRunning = true;
  updateActionAvailability();
  try {
    await operation();
    if (successMessage) setStatus(successMessage);
    return true;
  } catch (error) {
    if (error instanceof AdminSessionError) setConnectionState(false);
    setStatus(error.message || 'Operation failed.', true);
    return false;
  } finally {
    operationRunning = false;
    updateActionAvailability();
  }
}

function setConnectionState(connected, email = '') {
  const checking = connected === 'checking';
  const ready = connected === true;
  connectionChecking = checking;
  connectionState.dataset.connected = String(ready);
  connectionState.textContent = checking ? 'Connecting...' : ready ? 'Connected' : 'Sign in required';
  connectionState.title = ready && email ? `Connected as ${email}` : '';
  signInButton.hidden = ready || checking;
  updateActionAvailability();
}

function updateActionAvailability() {
  scanButton.disabled = operationRunning || connectionChecking;
}

function setStatus(message, error = false) {
  status.textContent = message;
  status.classList.toggle('error', error);
}
