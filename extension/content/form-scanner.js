globalThis.__yuqiApplicationCopilot = {
  scan() {
    const controls = [...document.querySelectorAll('input, select, textarea')]
      .filter((element) => isVisible(element) && !['hidden', 'submit', 'button', 'file'].includes(element.type));
    const fields = controls.map((element, index) => ({
      id: stableId(element, index),
      label: labelFor(element),
      type: element.type || element.tagName.toLowerCase(),
      autocomplete: element.autocomplete || '',
      required: element.required === true,
      options: element.tagName === 'SELECT'
        ? [...element.options].map((option) => option.text.trim()).filter(Boolean)
        : []
    })).filter((field) => field.label);
    return {
      pageType: classifyPage(controls),
      origin: location.origin,
      title: document.title,
      fields
    };
  },
  apply(values) {
    const controls = [...document.querySelectorAll('input, select, textarea')];
    let applied = 0;
    controls.forEach((element, index) => {
      const id = stableId(element, index);
      if (!Object.prototype.hasOwnProperty.call(values, id)) return;
      const value = String(values[id] ?? '');
      if (element.type === 'checkbox') {
        setChecked(element, value === 'true' || value === 'yes' || value === '1');
      } else if (element.type === 'radio') {
        const matches = element.value === value || labelFor(element).toLowerCase().includes(value.toLowerCase());
        if (!matches) return;
        setChecked(element, true);
      } else if (element.tagName === 'SELECT') {
        const option = [...element.options].find((item) => item.value === value || item.text.trim() === value);
        if (!option) return;
        element.value = option.value;
        dispatch(element);
      } else {
        setValue(element, value);
      }
      applied += 1;
    });
    return applied;
  },
  applyCredentials(credential) {
    const controls = [...document.querySelectorAll('input')].filter(isVisible);
    const passwordInputs = controls.filter((element) => element.type === 'password');
    const usernameInput = controls.find((element) => isUsernameField(element));
    const applied = [];
    if (usernameInput) {
      setValue(usernameInput, credential.username);
      applied.push(labelFor(usernameInput) || 'Username');
    }
    passwordInputs.forEach((element) => {
      setValue(element, credential.password);
      applied.push(labelFor(element) || 'Password');
    });
    return { applied, passwordFields: passwordInputs.length };
  }
};

function classifyPage(controls) {
  const text = `${document.title} ${document.body?.innerText || ''}`.toLowerCase().slice(0, 50000);
  const passwordControls = controls.filter((element) => element.type === 'password');
  const usesNewPassword = passwordControls.some((element) => element.autocomplete === 'new-password');
  if (passwordControls.length >= 2 || (passwordControls.length >= 1 &&
      (usesNewPassword || /\b(create account|sign up|register|confirm password)\b/.test(text)))) return 'SIGN_UP';
  if (passwordControls.length >= 1) return 'SIGN_IN';
  if (/\b(job application|apply now|submit application|candidate)\b/.test(text)) return 'APPLICATION';
  return 'FORM';
}

function stableId(element, index) {
  return element.id || element.name || `field-${index}`;
}

function labelFor(element) {
  const explicit = element.id ? document.querySelector(`label[for="${CSS.escape(element.id)}"]`) : null;
  return (explicit?.innerText || element.closest('label')?.innerText || element.getAttribute('aria-label') ||
    element.placeholder || element.name || element.id || '').replace(/\s+/g, ' ').trim();
}

function isUsernameField(element) {
  const identity = `${element.type} ${element.autocomplete} ${element.name} ${element.id} ${labelFor(element)}`.toLowerCase();
  return element.type === 'email' || /\b(email|e-mail|username|user name)\b/.test(identity);
}

function setValue(element, value) {
  const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value')?.set;
  if (setter) setter.call(element, String(value ?? '')); else element.value = String(value ?? '');
  dispatch(element);
}

function setChecked(element, checked) {
  const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'checked')?.set;
  if (setter) setter.call(element, checked); else element.checked = checked;
  dispatch(element);
}

function dispatch(element) {
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

function isVisible(element) {
  const style = getComputedStyle(element);
  return style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0;
}
