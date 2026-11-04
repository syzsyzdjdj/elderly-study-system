import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveApiOrigin } from '../src/utils/api-origin.mjs'

test('web uses same-origin and native requires an explicit server', () => {
  assert.equal(resolveApiOrigin('', false), '')
  assert.throws(() => resolveApiOrigin('', true), /VITE_API_BASE/)
  assert.equal(resolveApiOrigin('https://study.example.com/', true), 'https://study.example.com')
})
test('rejects credentials, non-http URLs and accidental api path duplication', () => {
  for (const value of ['https://user:password@example.com', 'file:///test', 'https://example.com/api', 'https://example.com?secret=x']) {
    assert.throws(() => resolveApiOrigin(value, false))
  }
})
