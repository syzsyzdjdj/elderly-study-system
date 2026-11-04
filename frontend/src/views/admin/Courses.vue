<template>
  <div class="admin-page">
    <h3 class="page-title">📚 课程管理</h3>

    <van-button type="primary" block size="large" icon="plus" @click="openAdd">新增课程</van-button>

    <van-empty v-if="loaded && !list.length" description="暂无课程" image-size="90" />

    <!-- 课程卡片列表 -->
    <div class="admin-course-card" v-for="item in list" :key="item.id">
      <div class="course-line">
        <span class="c-title">{{ item.title }}</span>
        <van-tag :type="item.status === 1 ? 'success' : 'warning'">{{ item.status === 1 ? '上架' : '下架' }}</van-tag>
      </div>
      <div class="c-line">分类：{{ categoryName(item.categoryId) }}　时长：{{ item.duration || 0 }} 分钟　积分：{{ item.points || 0 }}</div>
      <div class="c-line">适用地区：{{ [item.province, item.city, item.district].filter(Boolean).join(' / ') || '全国通用' }}</div>
      <div class="c-line">{{ item.intro }}</div>
      <div class="user-actions">
        <van-button size="small" type="primary" plain @click="openEdit(item)">编辑</van-button>
        <van-button size="small" type="success" plain icon="records" @click="openLearners(item)">学习记录</van-button>
        <van-button size="small" type="warning" plain @click="toggleStatus(item)">
          {{ item.status === 1 ? '下架' : '上架' }}
        </van-button>
        <van-button size="small" type="danger" plain @click="removeCourse(item)">删除</van-button>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <van-popup v-model:show="showPopup" position="bottom" class="course-editor-popup" :style="{ height: '100dvh' }" :close-on-click-overlay="false">
      <div class="course-editor-shell">
        <header class="course-editor-header">
          <van-button size="small" plain @click="closeEditor" :disabled="saving || uploadingAny">返回</van-button>
          <h3>{{ isEdit ? '编辑课程' : '写一篇新课程' }}</h3>
          <van-button type="primary" size="small" :loading="saving" :disabled="uploadingAny" @click="saveCourse">保存课程</van-button>
        </header>
        <p class="course-editor-tip">先写标题和简介，再像写博客一样编排教学正文。保存后可在课程学习页查看。</p>
        <van-form @submit="saveCourse">
          <van-field
            class="course-title-input"
            v-model="form.title"
            label="标题"
            type="textarea"
            rows="1"
            autosize
            placeholder="请输入课程标题"
            :rules="[{ required: true, message: '请输入课程标题' }]"
          />
          <van-field label="分类">
            <template #input>
              <select v-model="form.categoryId" class="course-category-select" aria-label="课程分类"><option :value="null" disabled>请选择分类</option><option v-for="c in categoryColumns" :key="c.value" :value="c.value">{{ c.text }}</option></select>
            </template>
          </van-field>
          <van-field v-model="form.intro" label="简介" type="textarea" rows="2" autosize placeholder="一句话介绍课程" />
          <CourseBodyEditor :key="editorSession" ref="bodyEditor" v-model="form.content" @uploading="bodyUploading = $event" />
          <h4 class="course-editor-section">课程资料与展示</h4>
          <van-field v-model="form.duration" label="时长(分钟)" type="number" placeholder="例如 15" />
          <van-field v-model="form.points" label="积分" type="number" placeholder="例如 10" />
          <van-field v-model="form.province" label="适用省份" placeholder="留空=全国通用，如：广东省" />
          <van-field v-model="form.city" label="适用城市" placeholder="留空=全省/全国，如：深圳市" />
          <van-field v-model="form.district" label="适用区县" placeholder="可留空，如：南山区" />
          <van-field label="封面图片">
            <template #input>
              <van-uploader :after-read="(f) => onUpload(f, 'cover')" :max-count="1" accept="image/*" />
            </template>
          </van-field>
          <div class="current-file">当前封面：{{ form.cover || '未上传' }}</div>
          <img v-if="form.cover" class="editor-cover-preview" :src="mediaUrl(form.cover)" alt="课程封面预览" />
          <van-field v-model="form.videoUrl" label="视频地址" placeholder="可填写B站视频页或视频文件地址" />
          <van-field label="视频文件">
            <template #input>
              <van-uploader :after-read="(f) => onUpload(f, 'video')" :max-count="1" accept="video/*" />
            </template>
          </van-field>
          <div class="current-file">当前视频：{{ form.videoUrl || '未上传' }}</div>
          <div class="form-actions">
            <van-button block type="primary" native-type="submit" size="large" :loading="saving" :disabled="uploadingAny">保存课程</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 学习记录弹窗：该课程被哪些用户学过 -->
    <van-popup v-model:show="showLearners" position="bottom" round :style="{ maxHeight: '70%' }">
      <div class="popup-body">
        <h3 class="page-title">📖 学习记录：{{ learnerCourse?.title }}</h3>
        <div v-if="loadingLearners" class="tab-loading"><van-loading size="24" />加载中...</div>
        <van-empty v-else-if="!learners.length" description="还没有用户学习过这门课" image-size="80" />
        <van-cell v-for="r in learners" :key="r.userId" :title="r.userName || `用户 ${r.userId}`">
          <template #label>
            累计 {{ formatMinutes(r.studyTime) }} · 进度 {{ r.progress || 0 }}% ·
            最近 {{ formatDate(r.lastStudyTime) }}
          </template>
          <template #value>
            <van-tag :type="r.isFinish === 1 ? 'success' : 'warning'">
              {{ r.isFinish === 1 ? '已完成' : '学习中' }}
            </van-tag>
          </template>
        </van-cell>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { addCourse, deleteCourse, getCourseList, updateCourse } from '../../api/course'
import { getCategoryList } from '../../api/category'
import { uploadFile } from '../../api/upload'
import { getCourseLearners } from '../../api/study'
import CourseBodyEditor from '../../components/CourseBodyEditor.vue'
import { mediaUrl } from '../../utils/api-base'

const list = ref([])
const loaded = ref(false)
const showPopup = ref(false)
const isEdit = ref(false)
const bodyEditor = ref(null)
const editorSession = ref(0)
const bodyUploading = ref(false)
const mediaUploading = ref(0)
const uploadingAny = computed(() => bodyUploading.value || mediaUploading.value > 0)
const saving = ref(false)
let originalForm = ''
const closeEditor = async () => {
  if (JSON.stringify(form) !== originalForm) {
    try { await showConfirmDialog({ title: '还有未保存的修改', message: '返回将放弃本次修改，确认返回？' }) }
    catch { return }
  }
  showPopup.value = false
}
const form = reactive({
  id: null,
  title: '',
  categoryId: null,
  intro: '',
  duration: '',
  points: '',
  content: '',
  cover: '',
  videoUrl: '',
  province: '',
  city: '',
  district: ''
})

const categories = ref([])
const categoryColumns = ref([])

// ---------- 学习记录（该课程被哪些用户学过） ----------
const showLearners = ref(false)
const loadingLearners = ref(false)
const learnerCourse = ref(null)
const learners = ref([])

const openLearners = async (item) => {
  learnerCourse.value = item
  learners.value = []
  showLearners.value = true
  loadingLearners.value = true
  try {
    const res = await getCourseLearners(item.id)
    learners.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loadingLearners.value = false
  }
}

const formatMinutes = (min) => {
  const m = Number(min || 0)
  if (m < 60) return `${m}分钟`
  const h = Math.floor(m / 60)
  return m % 60 ? `${h}小时${m % 60}分钟` : `${h}小时`
}

const formatDate = (t) => {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

const categoryName = (id) => {
  const c = categories.value.find((x) => x.id === id)
  return c ? c.name : '未分类'
}

// Vant4 picker 的 modelValue 是数组，需与分类ID做转换
const onCategoryChange = (v) => {
  form.categoryId = Array.isArray(v) ? v[0] : v
}

const load = async () => {
  try {
    const res = await getCourseList({ page: 1, size: 100 })
    const data = res.data || {}
    list.value = data.list || data.records || (Array.isArray(data) ? data : [])
  } catch (e) {
    // 拦截器已提示
  }
  loaded.value = true
}

// ---------- 上传（封面/视频） ----------
const onUpload = async (file, field) => {
  mediaUploading.value++
  try {
    const res = await uploadFile(file.file)
    const url = res.data?.url || (typeof res.data === 'string' ? res.data : '')
    if (url) {
      form[field] = url
      file.status = 'done'
      file.message = ''
      showToast('上传成功')
    } else {
      file.status = 'failed'
      file.message = '上传失败'
    }
  } catch (e) {
    file.status = 'failed'
    file.message = '上传失败'
  } finally { mediaUploading.value-- }
}

// ---------- 新增 / 编辑 ----------
const openAdd = () => {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    title: '',
    categoryId: categoryColumns.value[0]?.value ?? null,
    intro: '',
    duration: '',
    points: '',
    content: '',
    cover: '',
    videoUrl: '',
    province: '',
    city: '',
    district: ''
  })
  showPopup.value = true
  editorSession.value++; bodyUploading.value = false; originalForm = JSON.stringify(form)
}

const openEdit = (item) => {
  isEdit.value = true
  Object.assign(form, {
    id: item.id,
    title: item.title || '',
    categoryId: item.categoryId ?? categoryColumns.value[0]?.value ?? null,
    intro: item.intro || '',
    duration: item.duration ?? '',
    points: item.points ?? '',
    content: item.content || '',
    cover: item.cover || '',
    videoUrl: item.videoUrl || '',
    province: item.province || '',
    city: item.city || '',
    district: item.district || ''
  })
  showPopup.value = true
  editorSession.value++; bodyUploading.value = false; originalForm = JSON.stringify(form)
}

const saveCourse = async () => {
  if (saving.value || uploadingAny.value) return
  if (!form.title.trim()) {
    showToast('请输入课程标题')
    return
  }
  if (form.categoryId == null) { showToast('请选择课程分类'); return }
  saving.value = true
  try {
    const payload = { ...form }
    payload.title = payload.title.trim()
    payload.content = bodyEditor.value?.getValue() ?? payload.content
    payload.duration = Number(payload.duration) || 0
    payload.points = Number(payload.points) || 0
    if (isEdit.value) {
      await updateCourse(payload)
    } else {
      await addCourse(payload)
    }
    showToast('保存成功')
    showPopup.value = false
    load()
  } catch (e) {
    // 拦截器已提示
  } finally { saving.value = false }
}

// ---------- 上下架 ----------
const toggleStatus = async (item) => {
  const next = item.status === 1 ? 0 : 1
  try {
    await updateCourse({ id: item.id, status: next })
    item.status = next
    showToast(next === 1 ? '已上架' : '已下架')
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 删除 ----------
const removeCourse = async (item) => {
  try {
    await showConfirmDialog({
      title: '删除课程',
      message: `确定删除课程「${item.title}」吗？删除后不可恢复！`
    })
  } catch (e) {
    return
  }
  try {
    await deleteCourse(item.id)
    showToast('删除成功')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(async () => {
  load()
  try {
    const res = await getCategoryList()
    const data = res.data || []
    const listAll = Array.isArray(data) ? data : data.list || []
    const flat = []
    const visit = (items, prefix = '') => items.forEach(c => { flat.push({ ...c, label: prefix + c.name }); if (c.children?.length) visit(c.children, prefix + c.name + ' / ') })
    visit(listAll)
    categories.value = flat
    categoryColumns.value = flat.map((c) => ({ text: c.label, value: c.id }))
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.van-popup.course-editor-popup { left:0; width:100%; max-width:none; margin-left:0; max-height:100dvh; border-radius:0; box-sizing:border-box; }
.course-editor-shell { max-width:1000px; margin:auto; padding:0 18px 40px; background:#fff; min-height:100%; }
.course-title-input :deep(textarea) { font-size:24px; font-weight:700; line-height:1.5; }
.course-editor-header { display:flex; align-items:center; justify-content:space-between; gap:10px; position:sticky; top:0; z-index:2; background:#fff; padding:14px 0; border-bottom:1px solid #e5ece6; }
.course-editor-header h3 { margin:0; font-size:20px; }
.course-editor-tip { color:#627467; font-size:14px; line-height:1.7; }
.course-category-select { width:100%; min-height:42px; border:1px solid #d7e4db; border-radius:8px; padding:8px; background:white; color:#244d32; }
.course-editor-section { margin:24px 0 12px; color:#315c40; }
.editor-cover-preview { display:block; max-width:100%; width:260px; max-height:180px; object-fit:contain; margin:12px 0; border-radius:10px; background:#f4f8f5; }
.current-file { overflow-wrap:anywhere; }
.tab-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  color: #999;
}
</style>
