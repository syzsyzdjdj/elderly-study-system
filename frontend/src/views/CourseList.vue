<template>
  <div class="course-list-page">
    <van-nav-bar title="全部课程" right-text="刷新" @click-right="refresh" />

    <!-- 分类筛选 -->
    <van-dropdown-menu>
      <van-dropdown-item v-model="filterCid" :options="categoryOptions" @change="onFilterChange" />
    </van-dropdown-menu>

    <!-- 课程分页列表（上拉加载） -->
    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多课程了" @load="onLoad">
      <div class="course-card" v-for="item in list" :key="item.id" @click="router.push(`/course/${item.id}`)">
        <CourseCover class="course-cover" :src="item.cover" :title="item.title" />
        <div class="course-info">
          <div class="course-title">{{ item.title }}</div>
          <div class="course-intro">{{ item.intro }}</div>
          <div class="course-meta">
            <van-tag plain type="primary">⏱ {{ item.duration || 0 }} 分钟</van-tag>
            <van-tag plain type="success">+{{ item.points || 0 }} 积分</van-tag>
          </div>
        </div>
      </div>
    </van-list>

    <div class="tabbar-holder"></div>

    <van-tabbar route>
      <van-tabbar-item replace to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/courses" icon="bookmark-o">课程</van-tabbar-item>
      <van-tabbar-item replace to="/ai" icon="chat-o">AI问答</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourseList } from '../api/course'
import { getCategoryList } from '../api/category'
import { useUserStore } from '../store/user'
import CourseCover from '../components/CourseCover.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const list = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
let generation = 0

// 分类筛选（0=全部）
const filterCid = ref(Number(route.query.cid) || 0)
const categoryOptions = ref([{ text: '全部分类', value: 0 }])

const onLoad = async () => {
  const requestGeneration = generation
  try {
    const params = { page: page.value, size: pageSize }
    if (store.userInfo?.province) params.province = store.userInfo.province
    if (store.userInfo?.city) params.city = store.userInfo.city
    if (filterCid.value) params.categoryId = filterCid.value
    const res = await getCourseList(params)
    if (requestGeneration !== generation) return
    const data = res.data || {}
    const rows = data.list || data.records || (Array.isArray(data) ? data : [])
    const total = Number(data.total ?? rows.length)
    if (page.value === 1) list.value = rows
    else list.value = list.value.concat(rows)
    page.value += 1
    loading.value = false
    if (rows.length === 0 || list.value.length >= total) finished.value = true
  } catch (e) {
    if (requestGeneration !== generation) return
    loading.value = false
    finished.value = true
  }
}

const refresh = () => {
  generation++
  page.value = 1
  list.value = []
  finished.value = false
  loading.value = true
  onLoad()
}

const onFilterChange = () => refresh()

onMounted(async () => {
  // 分类下拉选项（顶级 + 子级，带缩进）
  try {
    const res = await getCategoryList()
    const data = res.data || []
    const listAll = Array.isArray(data) ? data : data.list || []
    const tops = listAll.filter((c) => !c.parentId)
    const opts = [{ text: '全部分类', value: 0 }]
    tops.forEach((t) => {
      opts.push({ text: t.name, value: t.id })
      listAll
        .filter((c) => c.parentId === t.id)
        .forEach((c) => opts.push({ text: `　⤷ ${c.name}`, value: c.id }))
    })
    categoryOptions.value = opts
  } catch (e) {
    // 拦截器已提示
  }
})
</script>
