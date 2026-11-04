import test from 'node:test'
import assert from 'node:assert/strict'
import { managedPasswordError } from '../src/utils/managedPassword.mjs'

test('management password validation rejects missing defaults, blanks and bcrypt byte overflow', () => {
  for (const password of ['', undefined, 'short', ' '.repeat(12), '中'.repeat(25)]) {
    assert.ok(managedPasswordError(password))
  }
  assert.equal(managedPasswordError('Explicit-password-for-test'), '')
  assert.equal(managedPasswordError('中'.repeat(24)), '')
})
