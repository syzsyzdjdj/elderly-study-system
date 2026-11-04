import test from 'node:test'
import assert from 'node:assert/strict'
import { courseIllustrations, resolveCourseImage } from '../src/utils/courseIllustrations.mjs'

test('missing legacy lesson illustrations resolve to self-contained artwork', () => {
  assert.equal(Object.keys(courseIllustrations).length, 8)
  for (const [path, uri] of Object.entries(courseIllustrations)) {
    assert.equal(resolveCourseImage(path, () => { throw new Error('must not use missing upload') }), uri)
    assert.match(uri, /^data:image\/svg\+xml;charset=utf-8,/)
    const svg = decodeURIComponent(uri.split(',')[1])
    assert.match(svg, /非应用界面截图/)
    assert.doesNotMatch(svg, /<script|<image|href=|onload=|<foreignObject/)
  }
  assert.equal(resolveCourseImage('/uploads/custom.png', src => 'https://example.test' + src), 'https://example.test/uploads/custom.png')
})
