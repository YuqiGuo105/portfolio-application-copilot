import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';

const fixture = await readFile(new URL('./workable-fixture.html', import.meta.url), 'utf8');
const ripplingFixture = await readFile(new URL('./rippling-fixture.html', import.meta.url), 'utf8');
const scanner = await readFile(new URL('../content/form-scanner.js', import.meta.url), 'utf8');
const html = fixture.replace(/<script src="\.\.\/content\/form-scanner\.js"><\/script>/, '');
const dom = new JSDOM(html, {
  url: 'https://apply.workable.com/pony-dot-ai/j/FIXTURE/',
  runScripts: 'outside-only'
});
dom.window.CSS = { escape: (value) => String(value).replace(/[^a-zA-Z0-9_-]/g, '\\$&') };

for (const element of dom.window.document.querySelectorAll('*')) {
  element.getClientRects = () => [{ width: 100, height: 30 }];
}
dom.window.eval(scanner);

const copilot = dom.window.__yuqiApplicationCopilot;
const scan = copilot.scan();
assert.equal(scan.adapter, 'WORKABLE');
assert.equal(scan.pageType, 'APPLICATION');
assert.equal(scan.action.kind, 'FINAL_SUBMIT');
assert.equal(scan.outcome.kind, 'NONE');
assert.equal(scan.fields.length, 4);
assert.equal(scan.files.length, 1);
assert.equal(scan.files[0].id, 'resume');
assert.equal(scan.files[0].semanticKey, 'resume');

let submitCount = 0;
dom.window.document.querySelector('form').addEventListener('submit', (event) => {
  event.preventDefault();
  submitCount += 1;
});
const applied = await copilot.apply({
  firstname: 'Yuqi',
  lastname: 'Guo',
  email: 'fixture@example.test',
  motivation: 'Fixture-only distributed systems answer.'
});
assert.equal(applied, 4);
assert.equal(dom.window.document.querySelector('#firstname').value, 'Yuqi');
assert.equal(dom.window.document.querySelector('#lastname').value, 'Guo');
assert.equal(dom.window.document.querySelector('#email').value, 'fixture@example.test');
assert.equal(submitCount, 0, 'The extension must never submit the application.');

dom.window.history.replaceState({}, '', '/pony-dot-ai/j/FIXTURE/?success');
dom.window.document.title = 'Application received';
dom.window.document.body.insertAdjacentHTML('beforeend', '<main>Thank you for applying. We received your application.</main>');
const submittedScan = copilot.scan();
assert.equal(submittedScan.outcome.kind, 'SUBMITTED');
assert.match(submittedScan.outcome.confirmationText, /application received|thank you for applying/i);

const rippling = new JSDOM(ripplingFixture, {
  url: 'https://ats.rippling.com/company/jobs/example/application',
  runScripts: 'outside-only'
});
rippling.window.CSS = dom.window.CSS;
for (const element of rippling.window.document.querySelectorAll('*')) {
  element.getClientRects = () => [{ width: 100, height: 30 }];
}
rippling.window.eval(scanner);
const genderControl = rippling.window.document.querySelector('#gender');
const genderOptions = rippling.window.document.querySelector('#gender-options');
const locationControl = rippling.window.document.querySelector('#location');
const locationOptions = rippling.window.document.querySelector('#location-options');
genderControl.addEventListener('click', () => { genderOptions.hidden = false; });
locationControl.addEventListener('click', () => { locationOptions.hidden = false; });
genderOptions.querySelectorAll('[role="option"]').forEach((option) => {
  option.addEventListener('click', () => {
    genderControl.dataset.selected = option.textContent.trim();
    genderOptions.hidden = true;
  });
});
locationOptions.querySelectorAll('[role="option"]').forEach((option) => {
  option.addEventListener('click', () => {
    locationControl.dataset.selected = option.textContent.trim();
    locationOptions.hidden = true;
  });
});
const ripplingScan = rippling.window.__yuqiApplicationCopilot.scan();
assert.equal(ripplingScan.adapter, 'RIPPLING');
assert.equal(ripplingScan.pageType, 'APPLICATION');
assert.equal(ripplingScan.fields.length, 10, 'Search and anonymous controls must not enter the application workflow.');
assert.equal(ripplingScan.files.length, 2);
assert.deepEqual(Array.from(ripplingScan.files, (field) => field.semanticKey), ['resume', 'cover_letter']);
assert.deepEqual(Array.from(ripplingScan.fields, (field) => field.semanticKey), [
  'first_name', 'last_name', 'email', 'current_company', 'phone', 'linkedin_url', 'website_url', '', '', ''
]);
assert.equal(ripplingScan.fields.filter((field) => field.required).length, 7);
assert.equal(ripplingScan.fields.filter((field) => field.type === 'radio').length, 1, 'Radio choices must be one application field.');
const ripplingApplied = await rippling.window.__yuqiApplicationCopilot.apply({
  firstName: 'Yuqi',
  lastName: 'Guo',
  email: 'fixture@example.test',
  currentCompany: 'Goldman Sachs',
  phone: '+1 (385) 237-4754'
});
assert.equal(ripplingApplied, 5);
assert.equal(rippling.window.document.querySelector('#phone').value, '3852374754');

const choiceApplied = await rippling.window.__yuqiApplicationCopilot.apply([
  { id: 'gender', label: 'Gender', value: 'Decline to self-identify' },
  { id: 'location', label: 'Location', value: 'Salt Lake City, UT' },
  { id: 'radio:smsConsent', label: 'Consent to receiving text message updates', value: 'No' }
]);
assert.equal(choiceApplied, 3);
assert.equal(genderControl.dataset.selected, 'Decline to self-identify');
assert.equal(locationControl.dataset.selected, 'Salt Lake City, UT, United States');
assert.equal(rippling.window.document.querySelector('input[name="smsConsent"][value="no"]').checked, true);

const scannedFirstName = ripplingScan.fields.find((field) => field.semanticKey === 'first_name');
const firstNameInput = rippling.window.document.querySelector('#first-name');
firstNameInput.removeAttribute('name');
firstNameInput.id = 'rerendered-generated-id';
firstNameInput.value = '';
const rebound = await rippling.window.__yuqiApplicationCopilot.apply([{
  id: scannedFirstName.id,
  semanticKey: scannedFirstName.semanticKey,
  label: scannedFirstName.label,
  value: 'Yuqi'
}]);
assert.equal(rebound, 1, 'Semantic matching must survive an ATS rerender that changes the field id.');
assert.equal(firstNameInput.value, 'Yuqi');

console.log(JSON.stringify({ adapter: scan.adapter, fields: scan.fields.length, files: scan.files.length,
  action: scan.action.kind, applied, submitCount, submittedOutcome: submittedScan.outcome.kind,
  ripplingAdapter: ripplingScan.adapter, ripplingFields: ripplingScan.fields.length, ripplingApplied }));
