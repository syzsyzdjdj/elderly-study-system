<template>
  <div class="admin-page">
    <h3 class="page-title">🎁 积分福利与兑换</h3>

    <van-tabs v-model:active="tab" sticky @change="onTabChange">
      <van-tab title="奖品管理" name="prize" />
      <van-tab title="兑换记录" name="exchange" />
    </van-tabs>

    <!-- 奖品管理 -->
    <template v-if="tab === 'prize'">
      <van-button type="primary" block size="large" icon="plus" @click="openAdd">新增奖品</van-button>

      <div v-for="p in prizes" :key="p.id" class="admin-course-card">
        <div class="course-line">
          <span class="c-title">{{ p.name }}</span>
          <van-tag :type="p.status === 1 ? 'success' : 'default'">{{ p.status === 1 ? '上架' : '下架' }}</van-tag>
        </div>
        <div class="c-line">{{ p.description || '暂无说明' }}</div>
        <div class="c-line">兑换积分：{{ p.pointsCost }}　库存：{{ p.stock }}</div>
        <div class="user-actions">
          <van-button size="small" type="primary" plain @click="openEdit(p)">编辑</van-button>
          <van-button size="small" type="danger" plain @click="remove(p)">删除</van-button>
        </div>
      </div>
      <van-empty v-if="loaded && !prizes.length" description="暂无奖品" image-size="90" />
    </template>

    <!-- 兑换记录 -->
    <template v-else>
      <div class="filter-bar">
        <van-field
          v-model="statusFilter"
          label="状态"
          placeholder="PENDING/DONE/CANCEL"
          style="flex:1"
          clearable
          @keyup.enter="reloadExchanges"
        />
        <van-button size="small" type="primary" @click="reloadExchanges">查询</van-button>
      </div>

      <div v-for="e in exchanges" :key="e.id" class="admin-course-card">
        <div class="course-line">
          <span class="c-title">{{ e.prizeName }}</span>
          <van-tag :type="STATUS[e.status]?.[1] || 'default'">{{ STATUS[e.status]?.[0] || e.status }}</van-tag>
        </div>
        <div class="c-line">兑换人：{{ e.userName || `用户${e.userId}` }}　消耗：{{ e.pointsCost }} 积分</div>
        <div class="c-line">时间：{{ fmt(e.createTime) }}</div>
        <div class="user-actions" v-if="e.status === 'PENDING'">
          <van-button size="small" type="success" plain @click="handle(e, 'DONE')">确认发放</van-button>
          <van-button size="small" type="danger" plain @click="handle(e, 'CANCEL')">取消并退分</van-button>
        </div>
      </div>
      <van-empty v-if="loadedEx && !exchanges.length" description="暂无兑换记录" image-size="90" />
      <van-pagination
        v-model="exPage"
        :total-items="exTotal"
        :items-per-page="10"
        @change="loadExchanges"
      />
    </template>

    <!-- 奖品新增/编辑 -->
    <van-popup v-model:show="showPopup" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">{{ form.id ? '编辑奖品' : '新增奖品' }}</h3>
        <van-field v-model="form.name" label="名称" placeholder="如：话费充值券 10 元" />
        <van-field v-model="form.pointsCost" label="兑换积分" type="number" placeholder="如：200" />
        <van-field v-model="form.stock" label="库存" type="number" placeholder="如：50" />
        <van-field v-model="form.description" label="说明" type="textarea" rows="2" placeholder="奖品描述与领取方式" />
        <van-field v-model="form.cover" label="封面图URL" placeholder="可留空" />
        <van-field label="上架">
          <template #input>
            <van-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import {
  getAdminPrizeList, savePrize, deletePrize,
  getExchangePage, handleExchange
} from '../../api/prize'

const STATUS = { PENDING: ['待发放', 'warning'], DONE: ['已发放', 'success'], CANCEL: ['已取消', 'danger'] }

const tab = ref('prize')
const loaded = ref(false)
const prizes = ref([])
const saving = ref(false)
const showPopup = ref(false)
const form = reactive({ id: null, name: '', pointsCost: 100, stock: 1, description: '', cover: '', status: 1 })

// 兑换记录
const loadedEx = ref(false)
const exchanges = ref([])
const exPage = ref(1)
const exTotal = ref(0)
const statusFilter = ref('')

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

const loadPrizes = async () => {
  try {
    const res = await getAdminPrizeList()
    prizes.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const loadExchanges = async () => {
  try {
    const res = await getExchangePage({
      page: exPage.value,
      size: 10,
      status: statusFilter.value || undefined
    })
    exchanges.value = res.data?.records || []
    exTotal.value = Number(res.data?.total || 0)
  } catch (e) { /* 拦截器已提示 */ }
  loadedEx.value = true
}

const reloadExchanges = () => {
  exPage.value = 1
  loadExchanges()
}

const onTabChange = (name) => {
  if (name === 'exchange' && !loadedEx.value) loadExchanges()
}

const openAdd = () => {
  Object.assign(form, { id: null, name: '', pointsCost: 100, stock: 1, description: '', cover: '', status: 1 })
  showPopup.value = true
}
const openEdit = (p) => {
  Object.assign(form, {
    id: p.id, name: p.name, pointsCost: p.pointsCost, stock: p.stock,
    description: p.description || '', cover: p.cover || '', status: p.status
  })
  showPopup.value = true
}

const save = async () => {
  if (!form.name) { showToast('请填写奖品名称'); return }
  saving.value = true
  try {
    await savePrize({ ...form, pointsCost: Number(form.pointsCost), stock: Number(form.stock) })
    showToast('保存成功')
    showPopup.value = false
    loadPrizes()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

const remove = async (p) => {
  try {
    await showConfirmDialog({ title: '删除奖品', message: `确定删除「${p.name}」吗？` })
  } catch { return }
  try {
    await deletePrize(p.id)
    showToast('删除成功')
    loadPrizes()
  } catch (e) { /* 拦截器已提示 */ }
}

const handle = async (e, action) => {
  const tip = action === 'DONE' ? '确认已向该学员发放奖品？' : '取消兑换后积分将退回学员账户，确认？'
  try {
    await showConfirmDialog({ title: '处理兑换', message: tip })
  } catch { return }
  try {
    await handleExchange({ id: e.id, action })
    showToast(action === 'DONE' ? '已确认发放' : '已取消并退回积分')
    loadExchanges()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(loadPrizes)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 10px 0;
}
</style>
