<template>
  <div class="admin-page">
    <h3 class="page-title">🗂 分类管理</h3>

    <van-button type="primary" block size="large" icon="plus" @click="openAdd(null)">新增顶级分类</van-button>

    <!-- 自动分类 -->
    <div class="auto-cat-section">
      <van-button type="success" block size="large" icon="fire" :loading="autoLoading" @click="runAutoCategorize">
        一键自动分类
      </van-button>
      <p class="auto-cat-hint">根据课程标题和简介关键词，自动将课程归入对应分类</p>
    </div>

    <van-empty v-if="loaded && !tree.length" description="暂无分类" image-size="90" />

    <!-- 树形展示：顶级 + 子级缩进 -->
    <div class="cat-item" v-for="c in tree" :key="c.id">
      <div class="cat-row">
        <span class="cat-name">📁 {{ c.name }}</span>
        <div class="cat-actions">
          <van-button size="small" type="primary" plain @click="openAdd(c)">+子类</van-button>
          <van-button size="small" type="warning" plain @click="openEdit(c)">编辑</van-button>
          <van-button size="small" type="danger" plain @click="removeCategory(c)">删除</van-button>
        </div>
      </div>
      <div v-if="c.children && c.children.length">
        <div class="cat-row child" v-for="child in c.children" :key="child.id">
          <span class="cat-name">⤷ {{ child.name }}</span>
          <div class="cat-actions">
            <van-button size="small" type="warning" plain @click="openEdit(child)">编辑</van-button>
            <van-button size="small" type="danger" plain @click="removeCategory(child)">删除</van-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <van-popup v-model:show="showPopup" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">{{ isEdit ? '编辑分类' : '新增分类' }}</h3>
        <van-form @submit="saveCategory">
          <van-field
            v-model="form.name"
            label="分类名称"
            placeholder="请输入分类名称，如：手机操作"
            :rules="[{ required: true, message: '请输入分类名称' }]"
          />
          <van-field v-model="form.sort" label="排序号" type="number" placeholder="数字越小越靠前" />
          <van-cell title="上级分类" :value="parentName" />
          <div class="form-actions">
            <van-button block type="primary" native-type="submit" size="large">保存</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { addCategory, autoCategorize, deleteCategory, getCategoryList, updateCategory } from '../../api/category'

const tree = ref([])
const loaded = ref(false)
const showPopup = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, name: '', sort: '', parentId: null })
const parentName = ref('顶级分类')
const autoLoading = ref(false)

const buildTree = (list) => {
  const rows = list.map((c) => ({ ...c, children: [] }))
  const tops = rows.filter((c) => !c.parentId)
  rows.forEach((c) => {
    if (c.parentId) {
      const p = rows.find((x) => x.id === c.parentId)
      if (p) p.children.push(c)
      else tops.push(c) // 父级缺失时当作顶级
    }
  })
  tree.value = tops
}

const load = async () => {
  try {
    const res = await getCategoryList()
    const data = res.data || []
    const list = Array.isArray(data) ? data : data.list || []
    buildTree(list)
  } catch (e) {
    // 拦截器已提示
  }
  loaded.value = true
}

// ---------- 新增 / 编辑 ----------
const openAdd = (parent) => {
  isEdit.value = false
  Object.assign(form, { id: null, name: '', sort: '', parentId: parent ? parent.id : null })
  parentName.value = parent ? parent.name : '顶级分类'
  showPopup.value = true
}

const openEdit = (c) => {
  isEdit.value = true
  Object.assign(form, { id: c.id, name: c.name, sort: c.sort ?? '', parentId: c.parentId ?? null })
  parentName.value = c.parentId ? '子级分类' : '顶级分类'
  showPopup.value = true
}

const saveCategory = async () => {
  if (!form.name) {
    showToast('请输入分类名称')
    return
  }
  try {
    const payload = { name: form.name, parentId: form.parentId || null, sort: Number(form.sort) || 0 }
    if (isEdit.value) {
      await updateCategory({ id: form.id, ...payload })
    } else {
      await addCategory(payload)
    }
    showToast('保存成功')
    showPopup.value = false
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 删除 ----------
const removeCategory = async (c) => {
  try {
    await showConfirmDialog({
      title: '删除分类',
      message: `确定删除分类「${c.name}」吗？删除后不可恢复！`
    })
  } catch (e) {
    return
  }
  try {
    await deleteCategory(c.id)
    showToast('删除成功')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 自动分类 ----------
const runAutoCategorize = async () => {
  try {
    await showConfirmDialog({
      title: '一键自动分类',
      message: '将根据课程标题和简介的关键词，自动匹配最合适的分类。已有分类的课程也会重新匹配。是否继续？'
    })
  } catch (e) {
    return
  }
  autoLoading.value = true
  try {
    const res = await autoCategorize()
    const d = res.data || {}
    showToast(`分类完成：共 ${d.total} 门，匹配 ${d.matched}，未匹配 ${d.unmatched}`)
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    autoLoading.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>
.auto-cat-section {
  margin: 12px 0 16px;
}
.auto-cat-hint {
  font-size: 13px;
  color: #94a3b8;
  text-align: center;
  margin: 8px 0 0;
}
</style>
