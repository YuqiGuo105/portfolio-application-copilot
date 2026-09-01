globalThis.__yuqiApplicationCopilot = {
  scan() {
    const controls = formControls();
    const indexedControls = controls.map((element, index) => ({ element, index }));
    const fields = describeApplicationFields(indexedControls.filter(({ element }) => isVisible(element)));
    const files = indexedControls
      .filter(({ element }) => isUsableFileInput(element))
      .map(({ element, index }) => {
        const label = labelFor(element) || 'Resume or attachment';
        return { id: stableId(element, index), requirementId: stableId(element, index), label,
          semanticKey: attachmentKeyFor(label), accept: element.accept || '', required: element.required === true };
      });
    return { adapter: detectAdapter(location.hostname),
      pageType: classifyPage(controls), origin: location.origin, url: location.href, title: document.title,
      fields, files, action: detectPrimaryAction(), outcome: detectSubmissionOutcome() };
  },
  async apply(values) {
    const controls = formControls();
    const instructions = Array.isArray(values) ? values : Object.entries(values)
      .map(([id, value]) => ({ id, value }));
    const used = new Set();
    let applied = 0;
    for (const instruction of instructions) {
      const match = findControl(controls, instruction, used);
      if (!match) continue;
      const { element } = match;
      const value = instruction.value;
      if (isCustomCombobox(element)) {
        if (!await chooseCustomOption(element, value)) continue;
      } else if (element.type === 'checkbox') {
        setChecked(element, value === true || ['true', 'yes', '1'].includes(String(value).toLowerCase()));
      } else if (element.type === 'radio') {
        const radio = findRadioOption(element, value);
        if (!radio) continue;
        setChecked(radio, true);
      } else if (element.tagName === 'SELECT') {
        const option = findMatchingOption([...element.options], value);
        if (!option) continue;
        setValue(element, option.value);
      } else setValue(element, valueForElement(element, value));
      used.add(element);
      applied += 1;
    }
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

function formControls() {
  const declared = [...document.querySelectorAll('input, select, textarea, button, [role="combobox"], [aria-controls], [aria-haspopup], [aria-autocomplete], [tabindex], [contenteditable="true"]')];
  return [...declared, ...customPromptControls()]
    .filter((element, index, controls) => controls.indexOf(element) === index);
}
function customPromptControls() {
  return [...document.querySelectorAll('div, span')]
    .filter((element) => {
      const prompt = normalizeText(element.innerText || element.textContent);
      return /^(select|choose)( |\.|$)/.test(prompt) &&
        ![...element.children].some((child) => normalizeText(child.innerText || child.textContent) === prompt);
    })
    .map((element) => nearestInteractiveControl(element));
}
function nearestInteractiveControl(element) {
  let candidate = element;
  for (let depth = 0; candidate && depth < 5; depth += 1, candidate = candidate.parentElement) {
    if (candidate.id || candidate.getAttribute('name') ||
        candidate.matches('button, [role="combobox"], [aria-controls], [aria-haspopup], [aria-autocomplete], [tabindex]') ||
        typeof candidate.onclick === 'function') return candidate;
  }
  return element;
}
function isUsableFileInput(element) {
  return element.type === 'file' && !element.disabled;
}
function describeApplicationFields(indexedControls) {
  const fields = [];
  const radioGroups = new Set();
  indexedControls.forEach(({ element, index }) => {
    if (!isApplicationControl(element)) return;
    if (element.type === 'radio' && element.name) {
      const id = radioGroupId(element);
      if (radioGroups.has(id)) return;
      radioGroups.add(id);
      const label = radioGroupLabel(element);
      if (label) fields.push(describeField(element, index, { id, label }));
      return;
    }
    const field = describeField(element, index);
    if (field.label) fields.push(field);
  });
  return fields;
}
function findControl(controls, instruction, used) {
  const indexed = controls.map((element, index) => ({ element, index })).filter(({ element }) => !used.has(element));
  const exact = indexed.find(({ element, index }) => stableId(element, index) === instruction.id ||
    (element.type === 'radio' && radioGroupId(element) === instruction.id));
  if (exact) return exact;
  if (instruction.semanticKey) {
    const semantic = indexed.filter(({ element }) => semanticKeyFor(element) === instruction.semanticKey);
    if (semantic.length === 1) return semantic[0];
    const label = normalizeText(instruction.label);
    const labelled = semantic.find(({ element }) => normalizeText(labelFor(element)) === label);
    if (labelled) return labelled;
  }
  const label = normalizeText(instruction.label);
  return label ? indexed.find(({ element }) => normalizeText(labelFor(element)) === label) : null;
}
function describeField(element, index, override = {}) {
  const id = override.id || stableId(element, index);
  const label = override.label || labelFor(element);
  return { id, requirementId: element.type === 'radio' && element.name ? radioGroupId(element) : id,
    label, semanticKey: semanticKeyFor(element, label), type: isCustomCombobox(element) ? 'combobox' : element.type || element.tagName.toLowerCase(),
    autocomplete: element.autocomplete || '', required: element.required === true || element.getAttribute('aria-required') === 'true',
    options: element.tagName === 'SELECT' ? [...element.options].map(optionText).filter(Boolean) :
      element.type === 'radio' ? radioOptions(element) : customOptions(element) };
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
  const customCombobox = isCustomCombobox(element);
  const nativeControl = ['INPUT', 'SELECT', 'TEXTAREA'].includes(element.tagName);
  if (!nativeControl && !customCombobox) return false;
  if (!customCombobox && ['hidden', 'submit', 'button', 'file', 'reset', 'image', 'search'].includes(element.type)) return false;
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
function attachmentKeyFor(label) {
  const normalized = normalizeText(label);
  if (/\b(resume|résumé|cv|curriculum vitae)\b/.test(normalized)) return 'resume';
  if (/\bcover letter\b/.test(normalized)) return 'cover_letter';
  return 'attachment';
}
function fieldIdentity(element, label) {
  return [label, element.name, element.id, element.autocomplete, element.placeholder,
    element.getAttribute('data-testid'), element.getAttribute('data-automation-id'), element.getAttribute('aria-label')]
    .filter(Boolean).join(' ');
}
function normalizeText(value) {
  return String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}
function radioOptions(element) {
  if (element.type !== 'radio' || !element.name) return [];
  return [...document.querySelectorAll(`input[type="radio"][name="${CSS.escape(element.name)}"]`)]
    .map((option) => labelFor(option) || option.value).filter(Boolean);
}
function radioGroupId(element) { return `radio:${element.name}`; }
function radioGroupLabel(element) {
  const group = element.closest('fieldset, [role="radiogroup"], [role="group"]');
  const legend = group?.querySelector(':scope > legend');
  const labelled = labelledByText(group);
  return cleanLabel(legend?.textContent || labelled || contextualLabel(group) || radioQuestion(element) || element.name);
}
function radioQuestion(element) {
  const radios = element.name
    ? [...document.querySelectorAll(`input[type="radio"][name="${CSS.escape(element.name)}"]`)] : [element];
  let scope = (radios[0].closest('label') || radios[0]).parentElement;
  while (scope && !radios.every((radio) => scope.contains(radio))) scope = scope.parentElement;
  if (!scope) return '';
  let branch = radios[0].closest('label') || radios[0];
  while (branch.parentElement && branch.parentElement !== scope) branch = branch.parentElement;
  let sibling = branch.previousElementSibling;
  while (sibling) {
    const text = (sibling.innerText || sibling.textContent || '').replace(/\s+/g, ' ').trim();
    if (text && text.length <= 600 && !sibling.querySelector('input, select, textarea, [role="combobox"]')) return text;
    sibling = sibling.previousElementSibling;
  }
  return contextualLabel(scope);
}
function findRadioOption(element, value) {
  const radios = element.name
    ? [...document.querySelectorAll(`input[type="radio"][name="${CSS.escape(element.name)}"]`)] : [element];
  return radios.find((radio) => choiceMatches(radio.value, value) || choiceMatches(labelFor(radio), value));
}
function isCustomCombobox(element) {
  if (element.getAttribute('role') === 'combobox' || element.getAttribute('aria-haspopup') === 'listbox') return true;
  if (element.getAttribute('aria-autocomplete') === 'list') return true;
  const controlled = (element.getAttribute('aria-controls') || '').split(/\s+/).filter(Boolean)
    .map((id) => document.getElementById(id));
  if (controlled.some((target) => target?.getAttribute('role') === 'listbox')) return true;
  const prompt = normalizeText(element.placeholder || element.innerText || element.textContent);
  return /^(select|choose)( |$)/.test(prompt) && Boolean(contextualLabel(element));
}
function customOptions(element) {
  return linkedOptions(element).map(optionText).filter(Boolean);
}
function linkedOptions(element) {
  const ids = `${element.getAttribute('aria-controls') || ''} ${element.getAttribute('aria-owns') || ''}`.trim().split(/\s+/).filter(Boolean);
  return ids.flatMap((id) => optionElements(document.getElementById(id)));
}
async function chooseCustomOption(element, value) {
  element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, composed: true }));
  element.click();
  if (element.tagName === 'INPUT') setValue(element, value);
  const options = await waitForOptions(element);
  const option = findMatchingOption(options, value);
  if (!option) return false;
  option.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, composed: true }));
  option.click();
  dispatch(element);
  return true;
}
async function waitForOptions(element) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const linked = linkedOptions(element).filter(isVisible);
    const visible = [...document.querySelectorAll('[role="listbox"]')].filter(isVisible).flatMap(optionElements);
    const options = linked.length ? linked : visible;
    if (options.length) return options;
    await new Promise((resolve) => setTimeout(resolve, 75));
  }
  return [];
}
function optionElements(container) {
  if (!container) return [];
  const explicit = [...container.querySelectorAll('[role="option"], [role="menuitemradio"], option')];
  if (explicit.length) return explicit;
  return [...container.querySelectorAll('*')].filter((candidate) => {
    const text = optionText(candidate);
    if (!text || text.length > 200 || candidate.matches('input, select, textarea')) return false;
    return ![...candidate.children].some((child) => optionText(child) === text);
  });
}
function findMatchingOption(options, value) {
  return options.find((option) => choiceMatches(option.value, value) || choiceMatches(optionText(option), value));
}
function optionText(option) { return (option.innerText || option.textContent || '').replace(/\s+/g, ' ').trim(); }
function choiceMatches(candidate, requested) {
  const left = canonicalChoice(candidate);
  const right = canonicalChoice(requested);
  if (!left || !right) return false;
  return left === right || left.includes(right) || right.includes(left);
}
function canonicalChoice(value) {
  const normalized = normalizeText(value).replace(/\b(i am|i do|i would|myself)\b/g, '').replace(/\s+/g, ' ').trim();
  if (/\b(decline|prefer not|wish not|dont wish|do not wish)\b/.test(normalized)) return 'decline';
  if (/\bno\b/.test(normalized) && /\b(consent|text message|sms)\b/.test(normalized)) return 'no consent';
  if (/^no\b/.test(normalized)) return 'no';
  if (/^yes\b/.test(normalized)) return 'yes';
  return normalized;
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
  const identity = element.name || element.getAttribute('data-automation-id') || element.id;
  if (identity) return element.type === 'radio' ? `${identity}:${element.value || index}` : identity;
  const semanticKey = semanticKeyFor(element);
  if (semanticKey) return `semantic:${semanticKey}`;
  return `field-${index}`;
}
function labelFor(element) {
  const nativeLabels = [...(element.labels || [])].map((label) => label.innerText || label.textContent).filter(Boolean).join(' ');
  const labelledBy = (element.getAttribute('aria-labelledby') || '').split(/\s+/)
    .map((id) => document.getElementById(id)?.innerText || document.getElementById(id)?.textContent).filter(Boolean).join(' ');
  const describedBy = (element.getAttribute('aria-describedby') || '').split(/\s+/)
    .map((id) => document.getElementById(id)?.innerText || document.getElementById(id)?.textContent).filter(Boolean).join(' ');
  const contextual = contextualLabel(element);
  const ariaLabel = element.getAttribute('aria-label') || '';
  const genericAriaLabel = isGenericControlLabel(ariaLabel);
  const meaningfulAriaLabel = genericAriaLabel ? '' : ariaLabel;
  const nativeControl = ['INPUT', 'SELECT', 'TEXTAREA'].includes(element.tagName);
  const contextualLabelValue = genericAriaLabel && nativeControl ? '' : contextual;
  const explicitLabel = nativeLabels || labelledBy || meaningfulAriaLabel || contextualLabelValue || describedBy;
  if (explicitLabel) return cleanLabel(explicitLabel);
  if (ariaLabel) return cleanLabel(ariaLabel);
  return cleanLabel(element.placeholder || element.name || element.id || '');
}
function isGenericControlLabel(value) {
  return /^(select|choose|search|textbox|input)( |\.|$)/.test(normalizeText(value));
}
function labelledByText(element) {
  return (element?.getAttribute?.('aria-labelledby') || '').split(/\s+/)
    .map((id) => document.getElementById(id)?.innerText || document.getElementById(id)?.textContent).filter(Boolean).join(' ');
}
function contextualLabel(element) {
  const container = element?.closest?.('fieldset, [data-field], [role="group"], [role="radiogroup"], .field, .form-field');
  const candidate = container?.querySelector?.(':scope > label, :scope > legend, :scope > [data-label], :scope > h2, :scope > h3, :scope > h4');
  if (candidate && !candidate.contains(element)) return candidate.textContent;
  let local = element?.parentElement;
  for (let depth = 0; local && depth < 4; depth += 1) {
    const localHeading = local.querySelector?.(':scope > label, :scope > legend, :scope > [data-label], :scope > h1, :scope > h2, :scope > h3, :scope > h4');
    if (localHeading && !localHeading.contains(element)) return localHeading.textContent;
    const controls = local.querySelectorAll('input, select, textarea, [role="combobox"], button[aria-haspopup="listbox"]');
    const text = (local.innerText || local.textContent || '').replace(/\s+/g, ' ').trim();
    if (controls.length === 1 && text && text.length <= 180) {
      const cleaned = text.replace(/^(select|choose)(\.\.\.)?$/i, '').trim();
      if (cleaned) return cleaned;
    }
    local = local.parentElement;
  }
  let node = element;
  for (let depth = 0; node && depth < 5; depth += 1) {
    let sibling = node.previousElementSibling;
    while (sibling) {
      const labelled = sibling.matches?.('label, legend, h1, h2, h3, h4, [data-label]')
        ? sibling : sibling.querySelector?.('label, legend, h1, h2, h3, h4, [data-label]');
      const text = (labelled?.textContent || sibling.textContent || '').replace(/\s+/g, ' ').trim();
      if (text && text.length <= 180 && !sibling.querySelector?.('input, select, textarea, [role="combobox"]')) return text;
      sibling = sibling.previousElementSibling;
    }
    node = node.parentElement;
  }
  return '';
}
function cleanLabel(value) { return String(value || '').replace(/\s+/g, ' ').trim(); }
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
function valueForElement(element, value) {
  if (detectAdapter(location.hostname) !== 'RIPPLING' || semanticKeyFor(element) !== 'phone') return value;
  const digits = String(value ?? '').replace(/\D/g, '');
  return digits.length === 11 && digits.startsWith('1') ? digits.slice(1) : digits;
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
