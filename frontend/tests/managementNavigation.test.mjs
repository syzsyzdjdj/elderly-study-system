import test from 'node:test'
import assert from 'node:assert/strict'
import { isManager, managementMenus, managementDestination, assignableMenus, managementGuard } from '../src/utils/managementNavigation.mjs'

test('course managers see granted content pages and cannot navigate to administrative pages', () => {
  assert.equal(isManager('course_manager'), true)
  const menus = managementMenus('course_manager', [
    { path: '/admin/dashboard' }, { path: '/admin/courses' }, { path: '/admin/profile' }, { path: '/unknown' }
  ])
  assert.deepEqual(menus.map(menu => menu.path), ['/admin/courses', '/admin/profile'])
  assert.equal(managementDestination('/admin/dashboard', menus), '/admin/courses')
  assert.equal(managementDestination('/admin/profile', menus), '/admin/profile')
  assert.deepEqual(assignableMenus('course_manager').map(menu => menu.path), ['/admin/courses', '/admin/categories'])
})

test('route guard redirects managers to assigned pages and tolerates access token renewal', async () => {
  const store = { token: 'old', userInfo: { id: 7 }, roleKey: 'course_manager' }
  const fetchMenus = async () => {
    store.token = 'renewed'
    return { data: [{ path: '/admin/courses' }, { path: '/admin/profile' }] }
  }
  assert.deepEqual(await managementGuard({ path: '/home' }, store, fetchMenus), { path: '/admin/courses' })
  assert.deepEqual(await managementGuard({ path: '/admin/profile', redirectedFrom: { path: '/admin' } }, store, fetchMenus), { path: '/admin/courses' })
  assert.equal(await managementGuard({ path: '/admin/courses' }, store, fetchMenus), true)
  assert.deepEqual(await managementGuard({ path: '/admin/users' }, store, fetchMenus), { path: '/admin/courses' })
})

test('route guard fails closed on network failure and cannot restore menus after logout', async () => {
  const store = { token: 'token', userInfo: { id: 7 }, roleKey: 'admin' }
  assert.equal(await managementGuard({ path: '/admin/users' }, store, async () => { throw Error('offline') }), false)
  assert.deepEqual(await managementGuard({ path: '/admin/users' }, store, async () => {
    store.token = ''; store.roleKey = ''
    return { data: [{ path: '/admin/users' }] }
  }), { path: '/login' })
  assert.equal(store.managementMenus, undefined)
  assert.deepEqual(await managementGuard({ path: '/admin/users' }, { token: 'x', roleKey: 'user' }, async () => assert.fail()), { path: '/home' })
})

test('revoked menus disappear and unknown or learner roles never enter management', () => {
  assert.deepEqual(managementMenus('course_manager', [{ path: '/admin/profile' }]).map(menu => menu.path), ['/admin/profile'])
  assert.equal(managementDestination('/admin/courses', []), '/home')
  for (const role of ['user', '', 'custom']) {
    assert.equal(isManager(role), false)
    assert.deepEqual(managementMenus(role, [{ path: '/admin/courses' }]), [])
  }
})
