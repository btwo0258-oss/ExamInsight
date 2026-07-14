import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'

import './assets/styles/variables.css'
import './assets/styles/global.css'
import './assets/styles/markdown.css'
import './assets/styles/code-highlight.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useThemeStore(pinia).init()
app.use(router)

app.mount('#app')
