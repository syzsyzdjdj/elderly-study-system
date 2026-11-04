<template>
  <div class="admin-page">
    <h3 class="page-title">📝 操作日志审计</h3>

    <div class="filter-bar">
      <van-field v-model="module" label="模块" placeholder="如：课程管理" style="flex:1" clearable @keyup.enter="reload" />
      <van-field v-model="username" label="操作人" placeholder="用户名" style="flex:1" clearable @keyup.enter="reload" />
      <van-button size="small" type="primary" @click="reload">查询</van-button>
    </div>

    <!-- 桌面风格日志表格 -->
    <div class="table-wrap">
      <table class="log-table">
        <thead>
          <tr>
            <th style="width:150px">时间</th>
            <th style="width:110px">操作人</th>
            <th style="width:110px">模块</th>
            <th>操作</th>
            <th style="width:90px">方法</th>
            <th style="width:120px">IP</th>
            <th style="width:70px">结果</th>
            <th style="width:80px">耗时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id" @click="openDetail(r)">
            <td>{{ fmt(r.createTime) }}</td>
            <td>{{ r.username }}</td>
            <td>{{ r.module }}</td>
            <td class="desc">{{ r.description }}</td>
            <td>{{ r.requestMethod }}</td>
            <td>{{ r.ip }}</td>
            <td>
              <van-tag :type="r.status === 1 ? 'success' : 'danger'">
                {{ r.status === 1 ? '成功' : '失败' }}
              </van-tag>
            </td>
            <td>{{ r.costTime ?? '-' }}ms</td>
          </tr>
          <tr v-if="loaded && !list.length">
            <td colspan="8" class="empty-td">暂无日志</td>
          </tr>
        </tbody>
      </table>
    </div>

    <van-pagination
      v-model="page"
      :total-items="total"
      :items-per-page="size"
      @change="load"
    />

    <!-- 详情 -->
    <van-popup v-model:show="showDetail" position="bottom" round :style="{ maxHeight: '75%' }">
      <div class="popup-body">
        <h3 class="page-title">日志详情</h3>
        <van-cell title="操作人" :value="`${detail.username}（${detail.userId}）`" />
        <van-cell title="模块" :value="detail.module" />
        <van-cell title="操作描述" :label="detail.description" />
        <van-cell title="请求地址" :value="`${detail.requestMethod} ${detail.requestUrl}`" />
        <van-cell title="IP" :value="detail.ip" />
        <van-cell title="时间" :value="fmt(detail.createTime)" />
        <van-cell title="耗时" :value="`${detail.costTime ?? '-'} ms`" />
        <div class="detail-block" v-if="detail.requestParams">
          <div class="detail-label">请求参数</div>
          <pre>{{ detail.requestParams }}</pre>
        </div>
        <div class="detail-block" v-if="detail.errorMsg">
          <div class="detail-label">异常信息</div>
          <pre class="error-pre">{{ detail.errorMsg }}</pre>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getOpLogPage } from '../../api/oplog'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loaded = ref(false)
const module = ref('')
const username = ref('')

const showDetail = ref(false)
const detail = reactive({})

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  try {
    const res = await getOpLogPage({
      page: page.value,
      size,
      module: module.value || undefined,
      username: username.value || undefined
    })
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const reload = () => {
  page.value = 1
  load()
}

const openDetail = (r) => {
  Object.keys(detail).forEach((k) => delete detail[k])
  Object.assign(detail, r)
  showDetail.value = true
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.table-wrap {
  overflow-x: auto;
  background: #fff;
  border-radius: 8px;
}
.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 800px;
}
.log-table th,
.log-table td {
  border-bottom: 1px solid #ebedf0;
  padding: 9px 10px;
  text-align: left;
  white-space: nowrap;
}
.log-table th {
  background: #f7f8fa;
  color: #646566;
  font-weight: 600;
}
.log-table tbody tr {
  cursor: pointer;
}
.log-table tbody tr:hover {
  background: #f0f6ff;
}
.desc {
  white-space: normal;
  max-width: 260px;
}
.empty-td {
  text-align: center;
  color: #969799;
  padding: 30px 0;
}
.detail-block {
  margin: 12px 16px;
}
.detail-label {
  font-weight: 700;
  margin-bottom: 6px;
  color: #323233;
}
.detail-block pre {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
.error-pre {
  color: #ee0a24;
}
</style>
