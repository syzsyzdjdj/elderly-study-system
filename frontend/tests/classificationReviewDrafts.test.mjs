import test from 'node:test'
import assert from 'node:assert/strict'
import { ClassificationReviewDrafts } from '../src/utils/classificationReviewDrafts.mjs'

const first = { id: '1', question: '微信支付怎么操作', category: null, reviewToken: null }
const second = { id: '2', question: '怎么设置字体', category: null, reviewToken: null }

test('saving one question preserves unsaved choices on the other questions and across pagination', () => {
  const drafts = new ClassificationReviewDrafts()
  drafts.remember(first, '支付')
  drafts.remember(second, '设置')
  drafts.discard(first.id)
  drafts.restore([])
  assert.equal(drafts.restore([second]).selections['2'], '设置')
  const result = drafts.restore([{ ...first, category: '支付', reviewToken: 'new-token' }, second])
  assert.deepEqual(result.selections, { 1: '支付', 2: '设置' })
  assert.equal(result.discarded, 0)
})

test('a changed source question or a competing review invalidates only the affected draft', () => {
  const drafts = new ClassificationReviewDrafts()
  drafts.remember(first, '支付')
  drafts.remember(second, '设置')
  const result = drafts.restore([{ ...first, category: '未确定业务', reviewToken: 'other-review' }, second])
  assert.equal(result.discarded, 1)
  assert.deepEqual(result.selections, { 1: '未确定业务', 2: '设置' })
  const changed = drafts.restore([{ ...second, question: '修改后的新问题' }])
  assert.equal(changed.discarded, 1)
  assert.equal(changed.selections['2'], '')
})

test('obsolete taxonomy choices are removed instead of submitting a category missing from the dropdown', () => {
  const drafts = new ClassificationReviewDrafts()
  drafts.remember(first, '支付')
  const restored = drafts.restore([first], ['设置', '未确定业务'])
  assert.equal(restored.discarded, 1)
  assert.equal(restored.selections['1'], '')
})

test('reverting to the persisted label removes the draft and distinguishes legacy and pending tokens', () => {
  const drafts = new ClassificationReviewDrafts()
  const legacy = { ...first, category: '支付', reviewToken: '' }
  drafts.remember(legacy, '设置')
  drafts.remember(legacy, '支付')
  assert.equal(drafts.restore([legacy]).selections['1'], '支付')
  drafts.remember(legacy, '设置')
  assert.equal(drafts.restore([first]).discarded, 1)
})

test('large identifiers remain distinct instead of being coerced into unsafe JavaScript numbers', () => {
  const drafts = new ClassificationReviewDrafts()
  const a = { ...first, id: '9007199254740992' }, b = { ...second, id: '9007199254740993' }
  drafts.remember(a, '支付')
  drafts.remember(b, '设置')
  const { selections } = drafts.restore([a, b])
  assert.equal(selections[a.id], '支付')
  assert.equal(selections[b.id], '设置')
})
