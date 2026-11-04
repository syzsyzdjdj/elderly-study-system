import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import { managementGuard } from '../utils/managementNavigation.mjs'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') },
  { path: '/home', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/courses', name: 'courses', component: () => import('../views/CourseList.vue') },
  { path: '/course/:id', name: 'courseDetail', component: () => import('../views/CourseDetail.vue') },
  { path: '/study/:id', name: 'study', component: () => import('../views/Study.vue') },
  { path: '/ai', name: 'aiChat', component: () => import('../views/AiChat.vue') },
  { path: '/mine', name: 'mine', component: () => import('../views/Mine.vue') },
  { path: '/reminder', name: 'reminder', component: () => import('../views/ReminderPage.vue') },
  { path: '/display', name: 'displaySettings', component: () => import('../views/DisplaySettings.vue') },
  { path: '/favorites', name: 'favorites', component: () => import('../views/Favorites.vue') },
  { path: '/points', name: 'points', component: () => import('../views/Points.vue') },
  { path: '/prizes', name: 'prizes', component: () => import('../views/Prizes.vue') },
  { path: '/gov', name: 'govCenter', component: () => import('../views/GovCenter.vue') },
  { path: '/messages', name: 'messages', component: () => import('../views/Messages.vue') },
  { path: '/brief', name: 'brief', component: () => import('../views/Brief.vue') },
  { path: '/companion', name: 'companion', component: () => import('../views/Companion.vue') },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    redirect: '/admin/profile',
    children: [
      { path: 'dashboard', name: 'adminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'spark-tasks', name: 'adminSparkTasks', component: () => import('../views/admin/SparkTasks.vue') },
      { path: 'classification-review', name: 'adminClassificationReview', component: () => import('../views/admin/ClassificationReview.vue') },
      { path: 'users', name: 'adminUsers', component: () => import('../views/admin/Users.vue') },
      { path: 'courses', name: 'adminCourses', component: () => import('../views/admin/Courses.vue') },
      { path: 'categories', name: 'adminCategories', component: () => import('../views/admin/Categories.vue') },
      { path: 'announcements', name: 'adminAnnouncements', component: () => import('../views/admin/Announcements.vue') },
      { path: 'faq', name: 'adminFaq', component: () => import('../views/admin/Faq.vue') },
      { path: 'prizes', name: 'adminPrizes', component: () => import('../views/admin/Prizes.vue') },
      { path: 'gov', name: 'adminGov', component: () => import('../views/admin/GovAdmin.vue') },
      { path: 'companion', name: 'adminCompanion', component: () => import('../views/admin/Companion.vue') },
      { path: 'tts-audio', name: 'adminTtsAudio', component: () => import('../views/admin/TtsAudio.vue') },
      { path: 'dict', name: 'adminDict', component: () => import('../views/admin/Dict.vue') },
      { path: 'logs', name: 'adminLogs', component: () => import('../views/admin/OpLogs.vue') },
      { path: 'roles', name: 'adminRoles', component: () => import('../views/admin/Roles.vue') },
      { path: 'settings', name: 'adminSettings', component: () => import('../views/admin/Settings.vue') },
      { path: 'profile', name: 'adminProfile', component: () => import('../views/admin/Profile.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由与菜单使用同一份服务端分配结果。
router.beforeEach(to => managementGuard(to, useUserStore(), async () => {
  const { getMyMenus } = await import('../api/role')
  return getMyMenus()
}))

export default router
