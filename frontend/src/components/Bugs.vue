<script setup>
import {Setting} from "@element-plus/icons-vue";

defineProps({
  msg: {
    type: String,
    required: true
  }
})
const tableData = []
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-header style="text-align: left; font-size: 22px">
      All Bugs
    </el-header>

    <el-main>
      <el-scrollbar>
        <el-table :data="tableData"
                  :default-sort="{ prop: 'date', order: 'descending' }"
                  stripe>
          <el-table-column prop="filename" label="File Name" width="400"/>
          <el-table-column prop="isoLevel" label="Isolation Level" width="220"/>
          <el-table-column prop="checker" label="Checker" width="200"/>
          <el-table-column prop="date" label="Date" sortable/>
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
