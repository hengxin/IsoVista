<script setup>
import {onBeforeUnmount, onMounted, ref, watch, watchEffect} from "vue"
import {download_run, get_current_log, get_run_list} from "@/api/api.js";
import { useRouter } from 'vue-router'

const router = useRouter()

const tableData = ref([])

async function get_runs() {
  get_run_list().then(res => {
    // console.log(res.data)
    tableData.value = []
    for (let i = 0; i < res.data.length; i++) {
      tableData.value.push({
        id: res.data[i].run_id,
        dbType: res.data[i].db_type,
        dbIsolation: res.data[i].db_isolation,
        checkerIsolation: res.data[i].checker_isolation,
        histCount: res.data[i].hist_count,
        bugCount: res.data[i].bug_count,
        status: res.data[i].status,
        percentage: Math.round(res.data[i].percentage),
        date: res.data[i].timestamp,
      })
    }
    // console.log(tableData)
  })
}

function handleView(row) {
  router.push("/run_view/" + row.id)
}

function handleDownload(row) {
  console.log(row.id)
  download_run(row.id).then(res => {

    const { data, headers } = res
    const fileName = decodeURIComponent(escape(res.headers['file-name']))

    //const blob = new Blob([JSON.stringify(data)], ...)
    const blob = new Blob([data], {type: headers['content-type']})
    let dom = document.createElement('a')
    let url = window.URL.createObjectURL(blob)
    dom.href = url
    dom.download = decodeURI(fileName)
    dom.style.display = 'none'
    document.body.appendChild(dom)
    dom.click()
    dom.parentNode.removeChild(dom)
    window.URL.revokeObjectURL(url)
  })
}
let intervalRefreshList = null;
onMounted(async () => {
  await get_runs()
  console.log("start refresh list every 3s")
  intervalRefreshList = setInterval(() => {
    get_runs()
  }, 3000)
})

onBeforeUnmount(() => {
  console.log("stop refresh list")
  clearInterval(intervalRefreshList)
})

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleDateString(); // 或者使用其他格式化库
}

const dialogVisible = ref(false)
const currentLog = ref('')
let intervalRefreshLog = null
watch(dialogVisible, (newVal) => {
  if (newVal) {
    console.log("start refresh log every 0.5s")
    intervalRefreshLog = setInterval(() => {
      get_current_log().then(
          res => {
            currentLog.value = res.data
          }
      )
    }, 500);
  }
  if (!newVal) {
    console.log("stop refresh log")
    clearInterval(intervalRefreshLog)
  }
})
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-header>
      <span class="info">
        All Runs
      </span>
    </el-header>

    <el-main>
      <el-scrollbar>
        <el-table :data="tableData"
                  :default-sort="{ prop: 'id', order: 'ascending' }"
                  stripe
        >
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="bugCount" label="Bug Count" width="100"/>
          <el-table-column prop="histCount" label="Hist Count" width="100"/>
          <el-table-column prop="dbType" label="DB Type" width="120"/>
          <el-table-column prop="dbIsolation" label="DB Isolation Level" width="270"/>
          <el-table-column prop="checkerIsolation" label="Checker Isolation Level" width="200"/>
          <el-table-column prop="date" label="Date">
            <template #default="{ row }">
              {{ formatDate(row.date) }}
            </template>
          </el-table-column>
          <el-table-column label="Status" width="130">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'Finished' && row.bugCount === 0" type="success">
                Healthy
              </el-tag>
              <el-tag v-else-if="row.status === 'Finished' && row.bugCount > 0" type="danger">
                Buggy
              </el-tag>
              <el-progress v-else-if="row.status === 'Running'" :percentage="row.percentage" @click="dialogVisible = true"></el-progress>
              <el-tag v-else-if="row.status === 'Pending'" type="warning">
                Pending
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Operations" width="200">
            <template #default="scope">
              <el-button
                  link
                  size="small"
                  type="primary"
                  @click="handleView(scope.row)"
              >View</el-button
              >
              <el-button
                  link
                  size="small"
                  type="primary"
                  @click="handleDownload(scope.row)"
              >Download</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </el-scrollbar>
    </el-main>
    <el-dialog class="current-log" v-model="dialogVisible" title="Runtime logs">
      <pre>{{currentLog}}</pre>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.layout-container-demo .el-header {
  position: relative;
  color: var(--el-text-color-primary);
  margin-bottom: 20px;
}

.layout-container-demo .el-main {
  padding: 0;
}

.layout-container-demo .toolbar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  right: 20px;
}

.info {
  font-size: 28px;
  display: inline-block;
  margin-top: 20px;
  font-weight: bold;
}

.current-log {
  overflow: auto;
}
.current-log pre {
  white-space: pre-wrap;
  word-wrap: break-word;
}

</style>
