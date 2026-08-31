globalThis.__yuqiApplicationCopilot = {
  scan() {
    const controls = [...document.querySelectorAll('input, select, textarea')]
      .filter((element) => isVisible(element) && !['hidden', 'submit', 'button', 'file'].includes(element.type));
    return controls.map((element, index) => ({
      id: stableId(element, index),
      label: labelFor(element),
      type: element.type || element.tagName.toLowerCase(),
      options: element.tagName === 'SELECT'
        ? [...element.options].map((option) => option.text.trim()).filter(Boolean)
        : []
    })).filter((field) => field.label);
  },
  apply(values) {
    const controls = [...document.querySelectorAll('input, select, textarea')];
    let applied = 0;
    controls.forEach((element, index) => {
      const id = stableId(element, index);
      if (!Object.prototype.hasOwnProperty.call(values, id)) return;
      const value = String(values[id] ?? '');
      if (element.tagName === 'SELECT') {
        const option = [...element.options].find((item) => item.value === value || item.text.trim() === value);
        if (!option) return;
        element.value = option.value;
      } else {
        const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value')?.set;
        if (setter) setter.call(element, value); else element.value = value;
      }
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
      applied += 1;
    });
    return applied;
  }
};

function stableId(element, index) {
  return element.id || element.name || `field-${index}`;
}

function labelFor(element) {
  const explicit = element.id ? document.querySelector(`label[for="${CSS.escape(element.id)}"]`) : null;
  return (explicit?.innerText || element.closest('label')?.innerText || element.getAttribute('aria-label') ||
    element.placeholder || element.name || element.id || '').replace(/\s+/g, ' ').trim();
}

function isVisible(element) {
  const style = getComputedStyle(element);
  return style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0;
}
