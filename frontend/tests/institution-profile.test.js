import test from 'node:test';
import assert from 'node:assert/strict';
import { institutionProfileUpdate } from '../src/utils/institution-profile.js';

test('institution profile updates contain only the four permitted fields', () => {
  assert.deepEqual(
    institutionProfileUpdate({
      contactPhone: '02-1234-5678',
      contactEmail: 'qa@example.test',
      introduction: '소개',
      logoFileId: 'file-id',
      name: '변경 불가',
      institutionCode: 'CHANGED',
    }),
    {
      contactPhone: '02-1234-5678',
      contactEmail: 'qa@example.test',
      introduction: '소개',
      logoFileId: 'file-id',
    },
  );
});

test('blank text fields are sent as null, not empty strings', () => {
  assert.deepEqual(
    institutionProfileUpdate({ contactPhone: '  ', contactEmail: '', introduction: ' 소개 ', logoFileId: null }),
    { contactPhone: null, contactEmail: null, introduction: '소개', logoFileId: null },
  );
});
