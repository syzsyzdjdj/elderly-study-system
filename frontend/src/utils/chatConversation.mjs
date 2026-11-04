export const MAX_QUESTION_LENGTH = 1000

/** Send only the current page's completed conversation, never profile or stored history. */
export function conversationContext(messages) {
  return messages.filter(message => !message.thinking && !message.intro && !message.failed
      && ['user', 'ai'].includes(message.role) && typeof message.content === 'string' && message.content.trim())
    .slice(-6)
    .map(message => ({ role: message.role === 'ai' ? 'assistant' : 'user', content: message.content.slice(0, MAX_QUESTION_LENGTH) }))
}

export function readChatReply(data) {
  if (typeof data?.answer !== 'string' || !data.answer.trim()) throw new Error('Empty chat reply')
  return { role: 'ai', content: data.answer, source: data.source, notice: typeof data.notice === 'string' ? data.notice : '' }
}

export function replyLabel(source) {
  return { FAQ: '人工审核问答', AI: 'AI 生成回答，请结合实际核实', LOCAL: '本地知识库答复' }[source] || ''
}
