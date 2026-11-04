import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import { useUserStore } from './store/user'
import { restoreSession } from './utils/session'
import './styles.css'
import './app-overlays.css'
import './app-theme.css'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(Vant)
restoreSession(useUserStore(pinia)).finally(() => {
  app.use(router)
  app.mount('#app')
})
