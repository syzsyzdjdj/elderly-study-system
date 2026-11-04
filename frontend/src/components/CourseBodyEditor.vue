<template>
  <section class="course-body-editor" aria-label="课程正文编辑器">
    <div class="editor-heading">
      <strong>课程正文</strong>
      <div class="editor-modes" role="group" aria-label="正文编辑模式">
        <button v-for="item in modes" :key="item.key" type="button" :aria-pressed="mode === item.key" @click="switchMode(item.key)">{{ item.label }}</button>
      </div>
    </div>
    <div v-show="mode === 'edit'" ref="toolbar" class="blog-toolbar" role="toolbar" aria-label="正文排版工具">
      <span class="ql-formats"><select class="ql-header" aria-label="段落样式"><option selected>正文</option><option value="2">大标题</option><option value="3">小标题</option></select></span>
      <span class="ql-formats"><button type="button" class="ql-bold" aria-label="加粗" title="加粗"></button><button type="button" class="ql-italic" aria-label="斜体" title="斜体"></button><button type="button" class="ql-underline" aria-label="下划线" title="下划线"></button></span>
      <span class="ql-formats"><button type="button" class="ql-list" value="ordered" aria-label="编号列表" title="编号列表"></button><button type="button" class="ql-list" value="bullet" aria-label="项目列表" title="项目列表"></button><button type="button" class="ql-blockquote" aria-label="引用" title="引用"></button></span>
      <span class="ql-formats"><button type="button" class="ql-link" aria-label="添加链接" title="添加链接"></button><button type="button" class="ql-image" aria-label="插入图片" title="上传并插入图片"></button><button type="button" class="ql-clean" aria-label="清除格式" title="清除格式"></button></span>
      <span class="editor-history"><button type="button" aria-label="撤销" title="撤销" @click="editor?.history.undo()">↶</button><button type="button" aria-label="重做" title="重做" @click="editor?.history.redo()">↷</button></span>
    </div>
    <div v-show="mode === 'edit'" ref="host"></div>
    <textarea v-if="mode === 'source'" v-model="source" class="html-source" aria-label="课程 HTML 源码" spellcheck="false" @input="emit('update:modelValue', source)"></textarea>
    <article v-if="mode === 'preview'" class="article blog-preview" aria-label="课程正文预览" v-html="safeHtml"></article>
    <div class="editor-footer"><span>{{ count }} 字</span><span>{{ uploading ? '图片上传中，请稍候…' : '可直接输入、粘贴图文，或用图片按钮插图' }}</span></div>
    <input ref="imageInput" type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden @change="onImage" />
    <van-dialog v-model:show="linkOpen" title="添加链接" show-cancel-button @confirm="insertLink">
      <van-field v-model="linkUrl" label="网址" placeholder="https://example.com" />
    </van-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Quill from 'quill'
import 'quill/dist/quill.snow.css'
import DOMPurify from 'dompurify'
import { showToast } from 'vant'
import { uploadFile } from '../api/upload'
import { mediaUrl } from '../utils/api-base'

const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'uploading'])
const modes = [{ key: 'edit', label: '写正文' }, { key: 'preview', label: '预览' }, { key: 'source', label: 'HTML' }]
const mode = ref('edit')
const source = ref(props.modelValue)
const toolbar = ref(null)
const host = ref(null)
const imageInput = ref(null)
const uploading = ref(false)
const linkOpen = ref(false)
const linkUrl = ref('https://')
let editor = null
let range = null
let alive = true
const sanitize = html => DOMPurify.sanitize(html || '', { USE_PROFILES: { html: true }, FORBID_TAGS: ['iframe', 'video', 'audio', 'style', 'form', 'input', 'button'] })
const safeHtml = computed(() => sanitize(source.value))
const count = computed(() => {
  const dom = new DOMParser().parseFromString(safeHtml.value, 'text/html')
  return Array.from((dom.body.textContent || '').replace(/\s/g, '')).length
})
const getValue = () => sanitize(source.value)
const update = () => {
  source.value = editor.getLength() <= 1 ? '' : editor.getSemanticHTML()
  emit('update:modelValue', source.value)
}
const switchMode = next => {
  if (mode.value === 'source') {
    source.value = sanitize(source.value)
    editor.clipboard.dangerouslyPasteHTML(source.value, 'silent')
    emit('update:modelValue', source.value)
  }
  mode.value = next
}
const pickImage = () => {
  range = editor.getSelection() || { index: editor.getLength() - 1, length: 0 }
  imageInput.value?.click()
}
const uploadImage = async file => {
  if (uploading.value) return
  if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type) || file.size > 10 * 1024 * 1024) {
    showToast('请选择10MB以内的PNG、JPG、WebP或GIF图片'); return
  }
  const position = range || editor.getSelection() || { index: editor.getLength() - 1, length: 0 }
  uploading.value = true; emit('uploading', true)
  editor.enable(false)
  try {
    const res = await uploadFile(file)
    const url = res.data?.url || (typeof res.data === 'string' ? res.data : '')
    if (!url || !alive) return
    editor.enable(true)
    const at = Math.min(position.index, editor.getLength() - 1)
    editor.insertEmbed(at, 'image', mediaUrl(url), 'user')
    editor.setSelection(at + 1, 0, 'silent')
    update()
  } finally {
    if (alive) { uploading.value = false; emit('uploading', false); editor.enable(true) }
  }
}
const onImage = async event => {
  const file = event.target.files?.[0]
  try { if (file) await uploadImage(file) }
  catch { /* 上传接口已显示错误，保留正文以便重试。 */ }
  finally { event.target.value = '' }
}
const pasteImage = event => {
  const file = Array.from(event.clipboardData?.files || []).find(f => f.type.startsWith('image/'))
  if (!file) return
  event.preventDefault(); event.stopImmediatePropagation()
  range = editor.getSelection() || { index: editor.getLength() - 1, length: 0 }
  uploadImage(file).catch(() => {})
}
const insertLink = () => {
  let url
  try { url = new URL(linkUrl.value); if (!['https:', 'http:'].includes(url.protocol)) throw new Error() }
  catch { showToast('请输入以https://或http://开头的网址'); return }
  editor.setSelection(range.index, range.length, 'silent')
  if (range.length) editor.format('link', url.href, 'user')
  else editor.insertText(range.index, url.href, 'link', url.href, 'user')
}
onMounted(() => {
  editor = new Quill(host.value, { theme: 'snow', placeholder: '像写博客一样编写课程：先写学习目标，再分步骤介绍操作…', modules: {
    toolbar: { container: toolbar.value, handlers: {
      image: pickImage,
      link(value) {
        if (!value) { editor.format('link', false, 'user'); return }
        range = editor.getSelection() || { index: editor.getLength() - 1, length: 0 }
        linkUrl.value = editor.getFormat(range).link || 'https://'; linkOpen.value = true
      }
    } }, history: { userOnly: true }
  } })
  editor.clipboard.dangerouslyPasteHTML(safeHtml.value, 'silent')
  editor.root.setAttribute('role', 'textbox')
  editor.root.setAttribute('aria-label', '课程正文（可视化编辑）')
  editor.root.setAttribute('aria-multiline', 'true')
  editor.history.clear()
  editor.on('text-change', update)
  editor.root.addEventListener('paste', pasteImage, true)
})
watch(() => props.modelValue, value => {
  if (value === source.value) return
  source.value = value || ''
  editor?.clipboard.dangerouslyPasteHTML(sanitize(source.value), 'silent')
})
onBeforeUnmount(() => { alive = false; editor?.off('text-change', update); editor?.root.removeEventListener('paste', pasteImage, true); editor = null })
defineExpose({ getValue })
</script>

<style scoped>
.course-body-editor { margin: 20px 0; border: 1px solid #cbded1; border-radius: 14px; overflow: hidden; background: white; }
.editor-heading,.editor-footer { display:flex; flex-wrap:wrap; justify-content:space-between; gap:10px; padding:12px; background:#f4f9f5; }
.editor-modes { display:flex; gap:6px; }
.editor-modes button { border:1px solid #cbded1; border-radius:8px; padding:7px 12px; background:white; color:#31523d; }
.editor-modes button[aria-pressed="true"] { background:#166534; color:white; }
.editor-footer { font-size:13px; color:#52675a; }
.blog-toolbar { display:flex; flex-wrap:wrap; gap:4px; background:#fafcfb; }
.blog-toolbar :deep(button) { width:34px; height:34px; }
.blog-toolbar :deep(.ql-formats) { margin-right:7px; }
:deep(.ql-container) { font-family:inherit; font-size:18px; border:0; }
:deep(.ql-editor) { min-height:360px; line-height:1.9; overflow-wrap:anywhere; padding:20px; }
:deep(.ql-editor img),.blog-preview :deep(img) { max-width:100%; height:auto; }
:deep(.ql-snow .ql-picker.ql-header .ql-picker-label::before), :deep(.ql-snow .ql-picker.ql-header .ql-picker-item::before) { content:'正文'; }
:deep(.ql-snow .ql-picker.ql-header [data-value="2"]::before) { content:'大标题'; }
:deep(.ql-snow .ql-picker.ql-header [data-value="3"]::before) { content:'小标题'; }
.html-source { width:100%; min-height:420px; box-sizing:border-box; border:0; padding:18px; font:14px/1.7 monospace; resize:vertical; }
.blog-preview { padding:24px; min-height:360px; overflow-wrap:anywhere; }
</style>
