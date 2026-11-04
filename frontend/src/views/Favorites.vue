<template>
  <div class="fav-page">
    <van-nav-bar title="我的收藏" left-arrow @click-left="router.back()" />

    <van-empty v-if="loaded && !list.length" description="还没有收藏课程，去课程里点爱心收藏吧" image-size="100" />

    <div class="course-card" v-for="item in list" :key="item.id">
      <CourseCover class="course-cover" :src="item.cover" :title="item.title" @click="router.push(`/course/${item.id}`)" />
      <div class="course-info" @click="router.push(`/course/${item.id}`)">
        <div class="course-title">{{ item.title }}</div>
        <div class="course-intro">{{ item.intro }}</div>
        <div class="course-meta">
          <van-tag plain type="primary">⏱ {{ item.duration || 0 }} 分钟</van-tag>
          <van-tag plain type="success">+{{ item.points || 0 }} 积分</van-tag>
        </div>
      </div>
      <van-button
        size="small"
        plain
        type="danger"
        class="unfav-btn"
        @click="onRemove(item)"
      >取消收藏</van-button>
    </div>

    <div class="tabbar-holder"></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getFavorites, removeFavorite } from '../api/favorite'
import CourseCover from '../components/CourseCover.vue'

const router = useRouter()
const list = ref([])
const loaded = ref(false)

const load = async () => {
  try {
    const res = await getFavorites()
    list.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loaded.value = true
  }
}

const onRemove = async (item) => {
  try {
    await showConfirmDialog({ title: '取消收藏', message: `确定取消收藏《${item.title}》吗？` })
  } catch {
    return
  }
  try {
    await removeFavorite(item.id)
    list.value = list.value.filter((c) => c.id !== item.id)
    showToast('已取消收藏')
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}
.unfav-btn {
  align-self: center;
  flex-shrink: 0;
}
</style>
