import { createRouter, createWebHashHistory } from "vue-router"
import Configuration from "@/components/Configuration.vue";
import BugList from "@/components/BugList.vue";
import BugView from "@/components/BugView.vue";
import RunList from "@/components/RunList.vue";
import RunView from "@/components/RunView.vue";

const routes = [
  { path: '/', component: Configuration },
  { path: '/configuration', component: Configuration },
  { path: '/runs', component: RunList },
  { path: '/bugs', component: BugList },
  { path: '/bug_view/:bug_id', component: BugView, props: true },
  { path: '/run_view/:run_id', component: RunView, props: true }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
