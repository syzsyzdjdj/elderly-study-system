// 跨刷新与分页保留尚未提交的人工选择；题目或标签版本变化后必须重新确认。
export class ClassificationReviewDrafts {
  #drafts = new Map()

  remember(question, category) {
    const id = String(question.id)
    if (category === (question.category ?? '')) {
      this.#drafts.delete(id)
    } else {
      this.#drafts.set(id, { question: question.question, token: question.reviewToken ?? null, category })
    }
  }

  restore(questions, allowedCategories) {
    const selections = {}
    let discarded = 0
    for (const question of questions) {
      const id = String(question.id)
      const draft = this.#drafts.get(id)
      if (draft && (draft.question !== question.question || draft.token !== (question.reviewToken ?? null)
          || (allowedCategories && !allowedCategories.includes(draft.category)))) {
        this.#drafts.delete(id)
        discarded++
      }
      selections[id] = this.#drafts.get(id)?.category ?? question.category ?? ''
    }
    return { selections, discarded }
  }

  discard(id) { this.#drafts.delete(String(id)) }
}
