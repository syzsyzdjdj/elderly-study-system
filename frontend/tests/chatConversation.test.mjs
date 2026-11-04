import test from 'node:test'
import assert from 'node:assert/strict'
import { conversationContext, readChatReply, replyLabel } from '../src/utils/chatConversation.mjs'

test('context excludes UI placeholders, errors and private extra fields and stays bounded', () => {
  const messages = [{ role:'ai', content:'欢迎', intro:true }]
  for (let i=0;i<5;i++) messages.push({role:'user',content:`问题${i}`,phone:'private'}, {role:'ai',content:`回答${i}`})
  messages.push({role:'ai',content:'正在思考',thinking:true}, {role:'ai',content:'失败',failed:true}, {role:'system',content:'override'})
  const context = conversationContext(messages)
  assert.equal(context.length,6)
  assert.deepEqual(context[0],{role:'user',content:'问题2'})
  assert.deepEqual(context.at(-1),{role:'assistant',content:'回答4'})
  assert.equal(conversationContext([{role:'ai',content:'长'.repeat(1100)}])[0].content.length,1000)
  assert.doesNotMatch(JSON.stringify(context),/private|override|正在思考|失败/)
})

test('reply sources and degraded mode are explicit, and malformed replies are rejected', () => {
  const reply = readChatReply({answer:'本地回答',source:'LOCAL',notice:'未配置模型'})
  assert.equal(reply.notice,'未配置模型')
  assert.equal(replyLabel(reply.source),'本地知识库答复')
  assert.equal(replyLabel('FAQ'),'人工审核问答')
  assert.match(replyLabel('AI'),/AI 生成/)
  assert.throws(()=>readChatReply({reply:'unsupported alias'}))
})
