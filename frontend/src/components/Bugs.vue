<script setup>
import {SuccessFilled} from "@element-plus/icons-vue"
import {ref} from "vue"

defineProps({
  msg: {
    type: String,
    required: false
  }
})
const tableData = ref([])

async function get_bugs() {
  await fetch('http://localhost:8000/bug_list', {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json'
    }
  }).then(response => {
    if (response.ok) {
      return response.json()
    }
  }).then(data => {
    for (let i = 0; i < data.length; i++) {
      tableData.value.push({
        filename: data[i].bug_dir,
        isoLevel: data[i].checker_isolation,
        checker: data[i].checker_type,
        date: data[i].timestamp
      })
    }
    console.log(tableData)
  })
}

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleDateString(); // 或者使用其他格式化库
}
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-header style="text-align: left; font-size: 22px">
      All Bugs
    </el-header>

    <el-main>
      <el-button type="success" @click="get_bugs">
        <el-icon>
          <SuccessFilled />
        </el-icon>
        <span>Get</span>
      </el-button>
      <el-scrollbar>
        <el-table :data="tableData"
                  :default-sort="{ prop: 'date', order: 'descending' }"
                  stripe
                  >
          <el-table-column type="index" width="80"/>
          <el-table-column prop="filename" label="File Name" width="400"/>
          <el-table-column prop="isoLevel" label="Isolation Level" width="220"/>
          <el-table-column prop="checker" label="Checker" width="200"/>
          <el-table-column prop="date" label="Date" sortable>
            <template #default="{ row }">
              {{ formatDate(row.date) }}
            </template>
          </el-table-column>>
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
