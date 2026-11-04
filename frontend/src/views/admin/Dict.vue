<template>
  <div class="admin-page">
    <h3 class="page-title">📖 系统基础字典</h3>

    <div class="dict-wrap">
      <!-- 左：字典类型 -->
      <div class="dict-types">
        <van-button size="small" type="primary" block icon="plus" @click="openTypeAdd">新增类型</van-button>
        <div
          v-for="t in types"
          :key="t.id"
          class="dict-type"
          :class="{ on: currentCode === t.dictCode }"
          @click="selectType(t)"
        >
          <div class="type-name">{{ t.dictName }}</div>
          <div class="type-code">{{ t.dictCode }}</div>
          <div class="type-ops" @click.stop>
            <van-icon name="edit" size="15" @click="openTypeEdit(t)" />
            <van-icon name="delete-o" size="15" @click="removeType(t)" />
          </div>
        </div>
        <van-empty v-if="loaded && !types.length" description="暂无字典类型" image-size="60" />
      </div>

      <!-- 右：字典项 -->
      <div class="dict-items">
        <div v-if="currentCode" class="items-head">
          <span class="items-title">字典项（{{ currentCode }}）</span>
          <van-button size="small" type="primary" icon="plus" @click="openItemAdd">新增字典项</van-button>
        </div>
        <van-empty v-else description="请先选择左侧字典类型" image-size="80" />

        <van-cell
          v-for="i in items"
          :key="i.id"
          :title="i.itemText"
          :value="i.itemValue"
          :label="`排序 ${i.sort ?? 0} · ${i.status === 1 ? '启用' : '停用'}`"
          size="large"
        >
          <template #right-icon>
            <van-icon name="edit" size="16" class="op-icon" @click="openItemEdit(i)" />
            <van-icon name="delete-o" size="16" class="op-icon danger" @click="removeItem(i)" />
          </template>
        </van-cell>
      </div>
    </div>

    <!-- 类型弹窗 -->
    <van-popup v-model:show="showType" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">{{ typeForm.id ? '编辑类型' : '新增类型' }}</h3>
        <van-field v-model="typeForm.dictName" label="名称" placeholder="如：课程类别" />
        <van-field v-model="typeForm.dictCode" label="编码" placeholder="如：course_category（创建后不建议修改）" :disabled="!!typeForm.id" />
        <van-field v-model="typeForm.remark" label="备注" placeholder="可选" />
        <van-field label="启用">
          <template #input>
            <van-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button block type="primary" size="large" @click="saveType">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 字典项弹窗 -->
    <van-popup v-model:show="showItem" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">{{ itemForm.id ? '编辑字典项' : '新增字典项' }}</h3>
        <van-field v-model="itemForm.itemText" label="显示文本" placeholder="如：政务办事" />
        <van-field v-model="itemForm.itemValue" label="值" placeholder="如：gov" />
        <van-field v-model="itemForm.sort" label="排序" type="number" placeholder="数字越小越靠前" />
        <van-field label="启用">
          <template #input>
            <van-switch v-model="itemForm.status" :active-value="1" :inactive-value="0" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button block type="primary" size="large" @click="saveItem">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import {
  getDictTypes, getDictItems,
  saveDictType, deleteDictType, saveDictItem, deleteDictItem
} from '../../api/dict'

const types = ref([])
const items = ref([])
const loaded = ref(false)
const currentCode = ref('')

const showType = ref(false)
const typeForm = reactive({ id: null, dictName: '', dictCode: '', remark: '', status: 1 })
const showItem = ref(false)
const itemForm = reactive({ id: null, dictCode: '', itemText: '', itemValue: '', sort: 0, status: 1 })

const loadTypes = async () => {
  try {
    const res = await getDictTypes()
    types.value = res.data || []
    if (!currentCode.value && types.value.length) selectType(types.value[0])
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const selectType = async (t) => {
  currentCode.value = t.dictCode
  try {
    const res = await getDictItems(t.dictCode)
    items.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
}

const openTypeAdd = () => {
  Object.assign(typeForm, { id: null, dictName: '', dictCode: '', remark: '', status: 1 })
  showType.value = true
}
const openTypeEdit = (t) => {
  Object.assign(typeForm, { id: t.id, dictName: t.dictName, dictCode: t.dictCode, remark: t.remark || '', status: t.status ?? 1 })
  showType.value = true
}
const saveType = async () => {
  if (!typeForm.dictName || !typeForm.dictCode) { showToast('请填写名称和编码'); return }
  try {
    await saveDictType({ ...typeForm })
    showToast('保存成功')
    showType.value = false
    loadTypes()
  } catch (e) { /* 拦截器已提示 */ }
}
const removeType = async (t) => {
  try { await showConfirmDialog({ title: '删除类型', message: `删除「${t.dictName}」将同时删除其字典项，确认？` }) } catch { return }
  try {
    await deleteDictType(t.id)
    showToast('删除成功')
    if (currentCode.value === t.dictCode) {
      currentCode.value = ''
      items.value = []
    }
    loadTypes()
  } catch (e) { /* 拦截器已提示 */ }
}

const openItemAdd = () => {
  Object.assign(itemForm, { id: null, dictCode: currentCode.value, itemText: '', itemValue: '', sort: 0, status: 1 })
  showItem.value = true
}
const openItemEdit = (i) => {
  Object.assign(itemForm, { id: i.id, dictCode: i.dictCode, itemText: i.itemText, itemValue: i.itemValue, sort: i.sort ?? 0, status: i.status ?? 1 })
  showItem.value = true
}
const saveItem = async () => {
  if (!itemForm.itemText || !itemForm.itemValue) { showToast('请填写文本和值'); return }
  try {
    await saveDictItem({ ...itemForm, sort: Number(itemForm.sort) || 0 })
    showToast('保存成功')
    showItem.value = false
    const t = types.value.find((x) => x.dictCode === currentCode.value)
    if (t) selectType(t)
  } catch (e) { /* 拦截器已提示 */ }
}
const removeItem = async (i) => {
  try { await showConfirmDialog({ title: '删除字典项', message: `确定删除「${i.itemText}」吗？` }) } catch { return }
  try {
    await deleteDictItem(i.id)
    showToast('删除成功')
    const t = types.value.find((x) => x.dictCode === currentCode.value)
    if (t) selectType(t)
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(loadTypes)
</script>

<style scoped>
.dict-wrap {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.dict-types {
  width: 220px;
  flex-shrink: 0;
}
.dict-type {
  position: relative;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 12px;
  margin-top: 8px;
  cursor: pointer;
  border: 1px solid transparent;
}
.dict-type.on {
  background: #ecf3ff;
  border-color: #2563eb;
}
.type-name {
  font-weight: 700;
  font-size: 14px;
}
.type-code {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
}
.type-ops {
  position: absolute;
  right: 8px;
  top: 8px;
  display: flex;
  gap: 8px;
  color: #1989fa;
}
.dict-items {
  flex: 1;
  min-width: 0;
}
.items-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.items-title {
  font-weight: 700;
}
.op-icon {
  margin-left: 10px;
  color: #1989fa;
}
.op-icon.danger {
  color: #ee0a24;
}
</style>
