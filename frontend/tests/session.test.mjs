import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

function load(post) {
  const scope = { axios: { post }, API_BASE: '/api', atob, Date, Number, JSON, Error, Promise }
  const code = fs.readFileSync(new URL('../src/utils/session.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replace(/export /g, '')
  vm.runInNewContext(code + '\nglobalThis.api={needsRefresh,refreshSession,restoreSession}', scope)
  return scope.api
}
const store = () => ({ token: 'old', authRevision: 0, remembered: true,
  setToken(token) { this.token=token; this.authRevision++ },
  setUserInfo(info) { this.info=info }, setRoleKey(role) { this.role=role },
  clearUser() { this.token=''; this.remembered=false; this.authRevision++ }
})
test('renewal is shared by concurrent requests and does not store passwords', async () => {
  let resolve; let calls=0
  const api=load(()=>{ calls++; return new Promise(r=>{resolve=r}) })
  const current=store(); const a=api.refreshSession(current);const b=api.refreshSession(current)
  assert.equal(a,b);assert.equal(calls,1)
  resolve({data:{code:200,data:{token:'new',userInfo:{id:1},roleKey:'user'}}})
  await a
  assert.equal(current.token,'new');assert.equal(current.role,'user')
})
test('a pending renewal cannot sign the user back in after logout', async () => {
  let resolve
  const api=load(()=>new Promise(r=>{resolve=r}));const current=store()
  const pending=api.refreshSession(current);current.clearUser()
  resolve({data:{code:200,data:{token:'must-not-be-used',userInfo:{id:1},roleKey:'user'}}})
  await assert.rejects(pending)
  assert.equal(current.token,'')
})
test('invalid session clears saved identity; offline failures do not', async () => {
  const denied=load(()=>Promise.reject({response:{status:401}}));const first=store()
  await assert.rejects(denied.refreshSession(first));assert.equal(first.token,'')
  const offline=load(()=>Promise.reject(new Error('offline')));const second=store()
  await assert.rejects(offline.refreshSession(second));assert.equal(second.token,'old')
})
test('startup skips renewal for fresh access tokens and users without keep-login', async () => {
  let calls=0;const api=load(()=>{calls++;throw new Error('unexpected')})
  const fresh=store();fresh.token='x.'+Buffer.from(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600})).toString('base64url')+'.x'
  await api.restoreSession(fresh);assert.equal(calls,0)
  const ordinary=store();ordinary.remembered=false
  await api.restoreSession(ordinary);assert.equal(calls,0);assert.equal(ordinary.token,'')
})
