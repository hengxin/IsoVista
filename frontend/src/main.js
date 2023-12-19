import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import router from "@/router/index.js";
import App from './App.vue'

const app = createApp(App)

app.use(ElementPlus)
app.use(router)
app.mount('#app')

