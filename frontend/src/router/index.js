import { createRouter, createWebHashHistory } from "vue-router"
import Dashboard from "@/components/Dashboard.vue";
import Bugs from "@/components/Bugs.vue";
import View from "@/components/View.vue";

const routes = [
  { path: '/', component: Dashboard },
  { path: '/dashboard', component: Dashboard },
  { path: '/bugs', component: Bugs },
  { path: '/view/:bug_id', component: View, props: true }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
