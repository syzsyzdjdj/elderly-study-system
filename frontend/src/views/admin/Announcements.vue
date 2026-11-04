<template>
  <div class="admin-page">
    <h3 class="page-title">📢 平台公告资讯</h3>

    <div class="filter-bar">
      <van-radio-group v-model="filterType" direction="horizontal" @change="load">
        <van-radio name="">全部</van-radio>
        <van-radio name="SYSTEM">系统公告</van-radio>
        <van-radio name="GOV">政务消息</van-radio>
      </van-radio-group>
      <van-button type="primary" size="small" icon="plus" @click="openAdd">发布公告</van-button>
    </div>

    <div v-for="a in list" :key="a.id" class="admin-course-card">
      <div class="course-line">
        <span class="c-title">{{ a.title }}</span>
        <van-tag :type="a.annType === 'GOV' ? 'primary' : 'warning'">
          {{ a.annType === 'GOV' ? '政务消息' : '系统公告' }}
        </van-tag>
        <van-tag :type="a.status === 1 ? 'success' : 'default'">{{ a.status === 1 ? '已发布' : '下架' }}</van-tag>
      </div>
      <div class="c-line ellipsis2">{{ a.content }}</div>
      <div class="c-line">发布时间：{{ fmt(a.publishTime || a.createTime) }}</div>
      <div class="user-actions">
        <van-button size="small" type="primary" plain @click="openEdit(a)">编辑</van-button>
        <van-button size="small" type="danger" plain @click="remove(a)">删除</van-button>
      </div>
    </div>
    <van-empty v-if="loaded && !list.length" description="暂无公告" image-size="90" />

    <van-pagination
      v-model="page"
      :total-items="total"
      :items-per-page="size"
      @change="load"
    />

    <!-- 新增/编辑 -->
    <van-popup v-model:show="showPopup" position="bottom" round :style="{ maxHeight: '85%' }">
      <div class="popup-body">
        <h3 class="page-title">{{ form.id ? '编辑公告' : '发布公告' }}</h3>
        <van-field v-model="form.title" label="标题" placeholder="请输入公告标题" />
        <van-field v-model="form.content" label="正文" type="textarea" rows="5" placeholder="请输入公告内容（发布后自动推送给老年学员）" />
        <van-field label="类型">
          <template #input>
            <van-radio-group v-model="form.annType" direction="horizontal">
              <van-radio name="SYSTEM">系统公告</van-radio>
              <van-radio name="GOV">政务消息</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field label="立即发布">
          <template #input>
            <van-switch v-model="published" :active-value="1" :inactive-value="0" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button block type="primary" size="large" :loading="saving" @click="save">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { getAnnouncementPage, saveAnnouncement, deleteAnnouncement } from '../../api/announcement'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loaded = ref(false)
const saving = ref(false)
const filterType = ref('')

const showPopup = ref(false)
const published = ref(1)
const form = reactive({ id: null, title: '', content: '', annType: 'SYSTEM' })

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  try {
    const res = await getAnnouncementPage({ page: page.value, size, type: filterType.value || undefined })
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const openAdd = () => {
  Object.assign(form, { id: null, title: '', content: '', annType: filterType.value || 'SYSTEM' })
  published.value = 1
  showPopup.value = true
}

const openEdit = (a) => {
  Object.assign(form, { id: a.id, title: a.title, content: a.content, annType: a.annType })
  published.value = a.status
  showPopup.value = true
}

const save = async () => {
  if (!form.title || !form.content) {
    showToast('请填写标题和正文')
    return
  }
  saving.value = true
  try {
    await saveAnnouncement({ ...form, status: published.value })
    showToast('保存成功')
    showPopup.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

const remove = async (a) => {
  try {
    await showConfirmDialog({ title: '删除公告', message: `确定删除「${a.title}」吗？` })
  } catch { return }
  try {
    await deleteAnnouncement(a.id)
    showToast('删除成功')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.ellipsis2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
