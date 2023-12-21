<script setup>
import {onMounted, ref} from "vue"
import {download_run, get_run_list} from "@/api/api.js";
import { useRouter } from 'vue-router'

const router = useRouter()

const tableData = ref([])

async function get_bugs() {
  get_run_list().then(res => {
    console.log(res.data)
    for (let i = 0; i < res.data.length; i++) {
      tableData.value.push({
        id: res.data[i].run_id,
        dbType: res.data[i].db_type,
        dbIsolation: res.data[i].db_isolation,
        checkerType: res.data[i].checker_type,
        checkerIsolation: res.data[i].checker_isolation,
        histCount: res.data[i].hist_count,
        bugCount: res.data[i].bug_count,
        date: res.data[i].timestamp,
      })
    }
    console.log(tableData)
  })
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

onMounted(async () => {
  await get_bugs()
})

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleDateString(); // 或者使用其他格式化库
}
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-header style="text-align: left; font-size: 22px">
      All Runs
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
          <el-table-column prop="dbType" label="DB Type" width="150"/>
          <el-table-column prop="dbIsolation" label="DB Isolation Level" width="250"/>
          <el-table-column prop="checkerType" label="Checker Type" width="150"/>
          <el-table-column prop="checkerIsolation" label="Checker Isolation Level" width="250"/>
          <el-table-column prop="date" label="Date">
            <template #default="{ row }">
              {{ formatDate(row.date) }}
            </template>
          </el-table-column>
          <el-table-column label="Operations" width="100">
            <template #default="scope">
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
  </el-container>
</template>

<style scoped>
.layout-container-demo .el-header {
  position: relative;
  background-color: var(--el-color-primary-light-7);
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
</style>
