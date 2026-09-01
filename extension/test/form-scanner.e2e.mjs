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

for (const element of dom.window.document.querySelectorAll('input, select, textarea, button')) {
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

let submitCount = 0;
dom.window.document.querySelector('form').addEventListener('submit', (event) => {
  event.preventDefault();
  submitCount += 1;
});
const applied = copilot.apply({
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
for (const element of rippling.window.document.querySelectorAll('input, select, textarea, button')) {
  element.getClientRects = () => [{ width: 100, height: 30 }];
}
rippling.window.eval(scanner);
const ripplingScan = rippling.window.__yuqiApplicationCopilot.scan();
assert.equal(ripplingScan.adapter, 'RIPPLING');
assert.equal(ripplingScan.pageType, 'APPLICATION');
assert.equal(ripplingScan.fields.length, 7, 'Search and anonymous controls must not enter the application workflow.');
assert.deepEqual(Array.from(ripplingScan.fields, (field) => field.semanticKey), [
  'first_name', 'last_name', 'email', 'current_company', 'phone', 'linkedin_url', 'website_url'
]);
assert.equal(ripplingScan.fields.filter((field) => field.required).length, 5);

console.log(JSON.stringify({ adapter: scan.adapter, fields: scan.fields.length, files: scan.files.length,
  action: scan.action.kind, applied, submitCount, submittedOutcome: submittedScan.outcome.kind,
  ripplingAdapter: ripplingScan.adapter, ripplingFields: ripplingScan.fields.length }));
