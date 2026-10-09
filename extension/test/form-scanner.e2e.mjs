import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { reviewedPageTarget } from '../shared/reviewed-page.js';

const fixture = await readFile(new URL('./workable-fixture.html', import.meta.url), 'utf8');
const ripplingFixture = await readFile(new URL('./rippling-fixture.html', import.meta.url), 'utf8');
const upstartFixture = await readFile(new URL('./upstart-fixture.html', import.meta.url), 'utf8');
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
}, scan.origin);
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
rippling.window.fetch = globalThis.fetch;
rippling.window.DataTransfer = class DataTransfer {
  constructor() {
    this.files = [];
    this.items = { add: (file) => this.files.push(file) };
  }
};
for (const element of rippling.window.document.querySelectorAll('*')) {
  element.getClientRects = () => [{ width: 100, height: 30 }];
}
rippling.window.eval(scanner);
const genderControl = rippling.window.document.querySelector('#gender');
const genderOptions = rippling.window.document.querySelector('#gender-options');
const raceControl = rippling.window.document.querySelector('#race');
const raceOptions = rippling.window.document.querySelector('#race-options');
const hispanicControl = rippling.window.document.querySelector('#hispanic');
const hispanicOptions = rippling.window.document.querySelector('#hispanic-options');
const locationControl = rippling.window.document.querySelector('#location');
const locationOptions = rippling.window.document.querySelector('#location-options');
genderControl.addEventListener('click', () => { genderOptions.hidden = false; });
raceControl.addEventListener('click', () => { raceOptions.hidden = false; });
hispanicControl.addEventListener('click', () => { hispanicOptions.hidden = false; });
locationControl.addEventListener('click', () => { locationOptions.hidden = false; });
genderOptions.querySelectorAll('span').forEach((option) => {
  option.addEventListener('click', () => {
    genderControl.dataset.selected = option.textContent.trim();
    genderOptions.hidden = true;
  });
});
[...raceOptions.querySelectorAll('span'), ...hispanicOptions.querySelectorAll('span')].forEach((option) => {
  option.addEventListener('click', () => {
    const control = option.closest('[role="listbox"]') === raceOptions ? raceControl : hispanicControl;
    control.dataset.selected = option.textContent.trim();
    option.closest('[role="listbox"]').hidden = true;
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
assert.equal(ripplingScan.fields.length, 12, 'Search and anonymous controls must not enter the application workflow.');
assert.equal(ripplingScan.files.length, 2);
assert.deepEqual(Array.from(ripplingScan.files, (field) => field.semanticKey), ['resume', 'cover_letter']);
const hiddenResumeInput = rippling.window.document.querySelector('#resume');
Object.defineProperty(hiddenResumeInput, 'files', { value: [], writable: true });
const attachedResume = await rippling.window.__yuqiApplicationCopilot.applyFile('resume', {
  name: 'Yuqi_Guo_Resume_SDE2.pdf', type: 'application/pdf',
  dataUrl: 'data:application/pdf;base64,JVBERi0xLjQKJSVFT0Y='
}, ripplingScan.origin);
assert.equal(attachedResume.name, 'Yuqi_Guo_Resume_SDE2.pdf');
assert.equal(hiddenResumeInput.files[0].name, 'Yuqi_Guo_Resume_SDE2.pdf');
assert.equal(rippling.window.__yuqiApplicationCopilot.fileStatus({ semanticKey: 'resume' }, ripplingScan.origin).name,
  'Yuqi_Guo_Resume_SDE2.pdf');
assert.equal(rippling.window.__yuqiApplicationCopilot.scan().files[0].selectedName,
  'Yuqi_Guo_Resume_SDE2.pdf');
assert.deepEqual(Array.from(ripplingScan.fields, (field) => field.semanticKey), [
  'first_name', 'last_name', 'email', 'current_company', 'phone', 'linkedin_url', 'website_url', '',
  '', '', '', ''
]);
assert.equal(ripplingScan.fields.find((field) => field.id === 'race').label, 'Please identify your race');
assert.deepEqual(Array.from(ripplingScan.fields.find((field) => field.id === 'gender').options),
  ['Male', 'Female', 'Choose not to disclose']);
assert.equal(ripplingScan.fields.filter((field) => field.required).length, 7);
assert.equal(ripplingScan.fields.filter((field) => field.type === 'radio').length, 1, 'Radio choices must be one application field.');
const ripplingApplied = await rippling.window.__yuqiApplicationCopilot.apply({
  firstName: 'Yuqi',
  lastName: 'Guo',
  email: 'fixture@example.test',
  currentCompany: 'Goldman Sachs',
  phone: '+1 (385) 237-4754'
}, ripplingScan.origin);
assert.equal(ripplingApplied, 5);
assert.equal(rippling.window.document.querySelector('#phone').value, '3852374754');

const choiceApplied = await rippling.window.__yuqiApplicationCopilot.apply([
  { id: 'gender', label: 'Gender', semanticKey: 'gender', value: 'Male' },
  { id: 'race', label: 'Please identify your race', semanticKey: 'race', value: 'Asian' },
  { id: 'hispanic', label: 'Are you Hispanic/Latino?', semanticKey: 'hispanic_latino', value: 'No' },
  { id: 'location', label: 'Location', value: 'Salt Lake City, UT' },
  { id: 'radio:smsConsent', label: 'Check Yes or No to indicate your agreement to receive text message updates from Rippling regarding your job application.', semanticKey: 'sms_consent', value: 'No' }
], ripplingScan.origin);
assert.equal(choiceApplied, 5);
assert.equal(genderControl.dataset.selected, 'Male');
assert.equal(raceControl.dataset.selected, 'Asian');
assert.equal(hispanicControl.dataset.selected, 'No');
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
}], ripplingScan.origin);
assert.equal(rebound, 1, 'Semantic matching must survive an ATS rerender that changes the field id.');
assert.equal(firstNameInput.value, 'Yuqi');

const upstart = new JSDOM(upstartFixture, {
  url: 'https://careers.upstart.com/jobs/software-engineer-upstart-bank-fixture',
  runScripts: 'outside-only'
});
upstart.window.CSS = dom.window.CSS;
for (const element of upstart.window.document.querySelectorAll('*')) {
  element.getClientRects = () => [{ width: 100, height: 30 }];
}
upstart.window.eval(scanner);
for (const control of upstart.window.document.querySelectorAll('#application button[aria-haspopup]')) {
  const options = upstart.window.document.querySelector(`#${control.id}-options`);
  control.addEventListener('click', () => { options.hidden = false; });
  options.querySelectorAll('[role="menuitemradio"]').forEach((option) => {
    option.addEventListener('click', () => {
      control.dataset.selected = option.textContent.trim();
      options.hidden = true;
    });
  });
}
const upstartScan = upstart.window.__yuqiApplicationCopilot.scan();
assert.equal(upstartScan.adapter, 'GENERIC');
assert.equal(upstartScan.pageType, 'APPLICATION');
assert.equal(upstartScan.files.length, 1);
assert.equal(upstartScan.fields.filter((field) => field.semanticKey === 'first_name').length, 1,
  'Job-alert controls must stay outside the application workflow.');
const locationPreference = upstartScan.fields.find((field) => field.type === 'checkbox-group');
assert.equal(locationPreference.label, 'Location Preference (required)');
assert.deepEqual(Array.from(locationPreference.options), ['San Mateo, CA', 'Columbus, OH', 'Austin, TX', 'Remote']);
assert.equal(locationPreference.required, true);
for (const id of ['current-location', 'sponsorship', 'familiarity', 'source', 'previous-employment',
  'disability', 'veteran', 'race', 'gender']) {
  assert.equal(upstartScan.fields.find((field) => field.id === id)?.type, 'combobox', `${id} must be scanned.`);
}
const upstartApplied = await upstart.window.__yuqiApplicationCopilot.apply([
  { id: locationPreference.id, label: locationPreference.label, value: 'Remote' },
  { id: 'sponsorship', label: 'Do you need immigration-related support or sponsorship?', value: 'Yes' },
  { id: 'disability', label: 'Disability Status', value: "No, I don't have a disability" },
  { id: 'veteran', label: 'Veteran Status', value: 'I am not a protected veteran' },
  { id: 'race', label: 'Race', value: 'Asian' },
  { id: 'gender', label: 'Gender', value: 'Male' }
], upstartScan.origin);
assert.equal(upstartApplied, 6);
assert.equal(upstart.window.document.querySelector('input[value="Remote"]').checked, true);
assert.equal(upstart.window.document.querySelector('input[value="Austin, TX"]').checked, false);
assert.equal(upstart.window.document.querySelector('#disability').dataset.selected, "No, I don't have a disability");
assert.equal(upstart.window.document.querySelector('#race').dataset.selected, 'Asian');
assert.equal(upstart.window.document.querySelector('#gender').dataset.selected, 'Male');

const reviewedPage = { tabId: 8, documentId: 'reviewed-document', origin: scan.origin };
assert.deepEqual(reviewedPageTarget(reviewedPage, { id: 8, url: scan.url }),
  { tabId: 8, documentIds: ['reviewed-document'] });
assert.throws(() => reviewedPageTarget(reviewedPage, { id: 9, url: scan.url }), /changed/);
assert.throws(() => reviewedPageTarget(reviewedPage, { id: 8, url: 'https://unreviewed.example/' }), /changed/);
assert.throws(() => reviewedPageTarget({ ...reviewedPage, documentId: null }, { id: 8, url: scan.url }), /changed/);
await assert.rejects(copilot.apply({ firstname: 'Never filled' }), /origin changed/);
dom.reconfigure({ url: 'https://unreviewed.example/form' });
await assert.rejects(copilot.apply({ firstname: 'Never filled' }, scan.origin), /origin changed/);
assert.equal(dom.window.document.querySelector('#firstname').value, 'Yuqi');
dom.window.document.body.insertAdjacentHTML('beforeend', '<input id="password" type="password">');
assert.throws(() => copilot.applyCredentials({ username: 'private', password: 'private' }, scan.origin), /origin changed/);
assert.equal(dom.window.document.querySelector('#password').value, '');

hiddenResumeInput.files = [];
rippling.window.fetch = async () => {
  rippling.reconfigure({ url: 'https://unreviewed.example/upload' });
  return { blob: async () => new rippling.window.Blob(['test'], { type: 'application/pdf' }) };
};
await assert.rejects(rippling.window.__yuqiApplicationCopilot.applyFile('resume', {
  name: 'private.pdf', dataUrl: 'data:application/pdf;base64,dGVzdA=='
}, ripplingScan.origin), /origin changed/);
assert.equal(hiddenResumeInput.files.length, 0, 'Navigation during attachment loading must prevent a write.');

console.log(JSON.stringify({ adapter: scan.adapter, fields: scan.fields.length, files: scan.files.length,
  action: scan.action.kind, applied, submitCount, submittedOutcome: submittedScan.outcome.kind,
  ripplingAdapter: ripplingScan.adapter, ripplingFields: ripplingScan.fields.length, ripplingApplied,
  upstartFields: upstartScan.fields.length, upstartApplied }));
