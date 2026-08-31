globalThis.__yuqiApplicationCopilot = {
  scan() {
    const controls = formControls().filter(isVisible);
    const indexedControls = controls.map((element, index) => ({ element, index }));
    const fields = indexedControls
      .filter(({ element }) => !['hidden', 'submit', 'button', 'file'].includes(element.type))
      .map(({ element, index }) => describeField(element, index))
      .filter((field) => field.label);
    const files = indexedControls
      .filter(({ element }) => element.type === 'file')
      .map(({ element, index }) => ({ id: stableId(element, index), label: labelFor(element) || 'Resume or attachment',
        accept: element.accept || '', required: element.required === true }));
    return { adapter: location.hostname.endsWith('workable.com') ? 'WORKABLE' : 'GENERIC',
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
  return { id: stableId(element, index), label: labelFor(element), type: element.type || element.tagName.toLowerCase(),
    autocomplete: element.autocomplete || '', required: element.required === true || element.getAttribute('aria-required') === 'true',
    options: element.tagName === 'SELECT' ? [...element.options].map((option) => option.text.trim()).filter(Boolean) : radioOptions(element) };
}
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
  const explicit = element.id ? document.querySelector(`label[for="${CSS.escape(element.id)}"]`) : null;
  const describedBy = (element.getAttribute('aria-describedby') || '').split(/\s+/)
    .map((id) => document.getElementById(id)?.innerText).filter(Boolean).join(' ');
  return (explicit?.innerText || element.closest('label')?.innerText || element.getAttribute('aria-label') ||
    element.placeholder || describedBy || element.name || element.id || '').replace(/\s+/g, ' ').trim();
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
