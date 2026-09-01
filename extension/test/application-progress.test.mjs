import assert from 'node:assert/strict';
import { calculateApplicationProgress } from '../shared/application-progress.js';

const fields = [
  { id: 'name', requirementId: 'name', required: true },
  { id: 'sponsor:yes', requirementId: 'sponsor', required: true },
  { id: 'sponsor:no', requirementId: 'sponsor', required: true },
  { id: 'headline', requirementId: 'headline', required: false }
];

assert.deepEqual(calculateApplicationProgress({
  fields,
  files: [{ id: 'resume', required: true }],
  resolutions: [
    { fieldId: 'name', value: 'Yuqi Guo' },
    { fieldId: 'sponsor:yes', value: true },
    { fieldId: 'sponsor:no', value: null }
  ],
  hasResume: true
}), { prepared: 3, total: 3, required: true, percent: 100 });

assert.deepEqual(calculateApplicationProgress({
  fields,
  files: [{ id: 'resume', required: true }],
  resolutions: [{ fieldId: 'name', value: 'Yuqi Guo' }],
  hasResume: false
}), { prepared: 1, total: 3, required: true, percent: 33 });

assert.deepEqual(calculateApplicationProgress({
  fields: [{ id: 'email', required: false }, { id: 'phone', required: false }],
  resolutions: [{ fieldId: 'email', value: 'test@example.com' }]
}), { prepared: 1, total: 2, required: false, percent: 50 });

console.log(JSON.stringify({ applicationProgressTests: 'passed' }));
