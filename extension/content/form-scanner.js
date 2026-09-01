globalThis.__yuqiApplicationCopilot = {
  scan() {
    const controls = formControls().filter(isVisible);
    const indexedControls = controls.map((element, index) => ({ element, index }));
    const fields = indexedControls
      .filter(({ element }) => isApplicationControl(element))
      .map(({ element, index }) => describeField(element, index))
      .filter((field) => field.label);
    const files = indexedControls
      .filter(({ element }) => element.type === 'file')
      .map(({ element, index }) => ({ id: stableId(element, index), requirementId: stableId(element, index),
        label: labelFor(element) || 'Resume or attachment', accept: element.accept || '', required: element.required === true }));
    return { adapter: detectAdapter(location.hostname),
      pageType: classifyPage(controls), origin: location.origin, url: location.href, title: document.title,
      fields, files, action: detectPrimaryAction(), outcome: detectSubmissionOutcome() };
  },
  apply(values) {
    const controls = formControls();
    let applied = 0;
    controls.forEach((element, index) => {
      const id = stableId(element, index);
      if (!Object.prototype.hasOwnProperty.call(values, id)) return;
      const value = values[id];
      if (element.type === 'checkbox') {
        setChecked(element, value === true || ['true', 'yes', '1'].includes(String(value).toLowerCase()));
      } else if (element.type === 'radio') {
        const matches = element.value === String(value) || labelFor(element).toLowerCase().includes(String(value).toLowerCase());
        if (!matches) return;
        setChecked(element, true);
      } else if (element.tagName === 'SELECT') {
        const option = [...element.options].find((item) => item.value === String(value) ||
          item.text.trim().toLowerCase() === String(value).trim().toLowerCase());
        if (!option) return;
        setValue(element, option.value);
      } else setValue(element, value);
      applied += 1;
    });
    return applied;
  },
  async applyFile(fieldId, payload) {
    const controls = formControls();
    const input = controls.find((element, index) => stableId(element, index) === fieldId);
    if (!input || input.type !== 'file') throw new Error('The resume upload field is no longer available.');
    if (!payload?.dataUrl || !payload?.name) throw new Error('The selected resume is invalid.');
    const blob = await fetch(payload.dataUrl).then((response) => response.blob());
    const file = new File([blob], payload.name, { type: payload.type || blob.type || 'application/pdf' });
    const transfer = new DataTransfer();
    transfer.items.add(file);
    input.files = transfer.files;
    dispatch(input);
    return { fieldId, name: file.name, size: file.size };
  },
  applyCredentials(credential) {
    const controls = [...document.querySelectorAll('input')].filter(isVisible);
    const passwordInputs = controls.filter((element) => element.type === 'password');
    const usernameInput = controls.find((element) => isUsernameField(element));
    const applied = [];
    if (usernameInput) { setValue(usernameInput, credential.username); applied.push(labelFor(usernameInput) || 'Username'); }
    passwordInputs.forEach((element) => { setValue(element, credential.password); applied.push(labelFor(element) || 'Password'); });
    return { applied, passwordFields: passwordInputs.length };
  }
};

function formControls() { return [...document.querySelectorAll('input, select, textarea')]; }
function describeField(element, index) {
  const id = stableId(element, index);
  const label = labelFor(element);
  return { id, requirementId: element.type === 'radio' && element.name ? `radio:${element.name}` : id,
    label, semanticKey: semanticKeyFor(element, label), type: element.type || element.tagName.toLowerCase(),
    autocomplete: element.autocomplete || '', required: element.required === true || element.getAttribute('aria-required') === 'true',
    options: element.tagName === 'SELECT' ? [...element.options].map((option) => option.text.trim()).filter(Boolean) : radioOptions(element) };
}
function detectAdapter(hostname) {
  const host = String(hostname || '').toLowerCase();
  if (host.endsWith('workable.com')) return 'WORKABLE';
  if (host === 'ats.rippling.com' || host.endsWith('.ats.rippling.com')) return 'RIPPLING';
  if (host.endsWith('greenhouse.io')) return 'GREENHOUSE';
  if (host.endsWith('lever.co')) return 'LEVER';
  return 'GENERIC';
}
function isApplicationControl(element) {
  if (['hidden', 'submit', 'button', 'file', 'reset', 'image', 'search'].includes(element.type)) return false;
  if (element.disabled || element.getAttribute('aria-hidden') === 'true') return false;
  const label = normalizeText(labelFor(element));
  const identity = normalizeText(fieldIdentity(element, label));
  if (element.getAttribute('role') === 'searchbox') return false;
  if (/^(search|search jobs|search options|type to search)$/.test(label)) return false;
  if (/\b(search input|job search|location search)\b/.test(identity)) return false;
  if (!label || /^(textbox|input|select|select\.\.\.|choose\.\.\.)$/.test(label)) {
    return Boolean(semanticKeyFor(element, label));
  }
  return true;
}
function semanticKeyFor(element, label = labelFor(element)) {
  const identity = normalizeText(fieldIdentity(element, label));
  if (/\b(first name|given name|given-name|firstname)\b/.test(identity)) return 'first_name';
  if (/\b(last name|family name|family-name|surname|lastname)\b/.test(identity)) return 'last_name';
  if (/\b(e mail|email|email address)\b/.test(identity)) return 'email';
  if (/\b(phone|telephone|mobile|tel)\b/.test(identity)) return 'phone';
  if (/\b(current company|current employer|organization|organisation)\b/.test(identity)) return 'current_company';
  if (/\b(linkedin|linked in)\b/.test(identity)) return 'linkedin_url';
  if (/\b(personal website|portfolio|website|web site)\b/.test(identity)) return 'website_url';
  if (/\b(city|address level2)\b/.test(identity)) return 'city';
  if (/\b(state|province|address level1)\b/.test(identity)) return 'state';
  if (/\b(postal|zip|postal code)\b/.test(identity)) return 'postal_code';
  if (/\b(country|country name)\b/.test(identity)) return 'country';
  return '';
}
function fieldIdentity(element, label) {
  return [label, element.name, element.id, element.autocomplete, element.placeholder,
    element.getAttribute('data-testid'), element.getAttribute('data-automation-id'), element.getAttribute('aria-label')]
    .filter(Boolean).join(' ');
}
function normalizeText(value) { return String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim(); }
function radioOptions(element) {
  if (element.type !== 'radio' || !element.name) return [];
  return [...document.querySelectorAll(`input[type="radio"][name="${CSS.escape(element.name)}"]`)]
    .map((option) => labelFor(option) || option.value).filter(Boolean);
}
function detectPrimaryAction() {
  const candidates = [...document.querySelectorAll('button, input[type="submit"]')].filter(isVisible).map((button) => ({
    text: (button.innerText || button.textContent || button.value || button.getAttribute('aria-label') || '').replace(/\s+/g, ' ').trim(), disabled: button.disabled === true
  })).filter((item) => item.text);
  const submit = candidates.find((item) => /submit application|send application|apply now/i.test(item.text));
  const next = candidates.find((item) => /continue|next|save and continue/i.test(item.text));
  return submit ? { kind: 'FINAL_SUBMIT', ...submit } : next ? { kind: 'CONTINUE', ...next } : { kind: 'NONE' };
}
function detectSubmissionOutcome() {
  const visibleText = `${document.title} ${document.body?.innerText || document.body?.textContent || ''}`
    .replace(/\s+/g, ' ').trim().slice(0, 50000);
  const successText = visibleText.match(/(?:application (?:has been )?(?:submitted|received)|thank you for applying|we received your application)/i)?.[0];
  const successUrl = /(?:[?&](?:success|submitted)=?(?:true|1)?(?:&|$)|\/(?:application-)?(?:success|submitted)(?:\/|$))/i
    .test(`${location.pathname}${location.search}`);
  if (!successText && !successUrl) return { kind: 'NONE' };
  return { kind: 'SUBMITTED', confirmationText: successText || 'ATS success route detected' };
}
function classifyPage(controls) {
  const text = `${document.title} ${document.body?.innerText || document.body?.textContent || ''}`.toLowerCase().slice(0, 50000);
  const passwords = controls.filter((element) => element.type === 'password');
  const usesNewPassword = passwords.some((element) => element.autocomplete === 'new-password');
  if (passwords.length >= 2 || (passwords.length >= 1 && (usesNewPassword || /\b(create account|sign up|register|confirm password)\b/.test(text)))) return 'SIGN_UP';
  if (passwords.length >= 1) return 'SIGN_IN';
  if (/\b(job application|apply now|submit application|candidate|personal information)\b/.test(text)) return 'APPLICATION';
  return 'FORM';
}
function stableId(element, index) {
  const identity = element.id || element.name;
  if (identity) return element.type === 'radio' ? `${identity}:${element.value || index}` : identity;
  return `field-${index}`;
}
function labelFor(element) {
  const nativeLabels = [...(element.labels || [])].map((label) => label.innerText || label.textContent).filter(Boolean).join(' ');
  const labelledBy = (element.getAttribute('aria-labelledby') || '').split(/\s+/)
    .map((id) => document.getElementById(id)?.innerText || document.getElementById(id)?.textContent).filter(Boolean).join(' ');
  const describedBy = (element.getAttribute('aria-describedby') || '').split(/\s+/)
    .map((id) => document.getElementById(id)?.innerText || document.getElementById(id)?.textContent).filter(Boolean).join(' ');
  return (nativeLabels || labelledBy || element.getAttribute('aria-label') || element.placeholder || describedBy ||
    element.name || element.id || '').replace(/\s+/g, ' ').trim();
}
function isUsernameField(element) {
  const identity = `${element.type} ${element.autocomplete} ${element.name} ${element.id} ${labelFor(element)}`.toLowerCase();
  return element.type === 'email' || /\b(email|e-mail|username|user name)\b/.test(identity);
}
function setValue(element, value) {
  const prototype = element.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype :
    element.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
  if (setter) setter.call(element, String(value ?? '')); else element.value = String(value ?? '');
  dispatch(element);
}
function setChecked(element, checked) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'checked')?.set;
  if (setter) setter.call(element, checked); else element.checked = checked;
  dispatch(element);
}
function dispatch(element) {
  element.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
  element.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
  element.dispatchEvent(new Event('blur', { bubbles: true, composed: true }));
}
function isVisible(element) {
  const style = getComputedStyle(element);
  return style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0;
}
