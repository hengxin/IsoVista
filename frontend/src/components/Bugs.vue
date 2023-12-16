<script setup>
import {Setting} from "@element-plus/icons-vue";

defineProps({
  msg: {
    type: String,
    required: true
  }
})
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-header style="text-align: right; font-size: 12px">
      <div class="toolbar">
        <el-dropdown>
          <el-icon style="margin-right: 8px; margin-top: 1px"
          ><setting
          /></el-icon>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>View</el-dropdown-item>
              <el-dropdown-item>Add</el-dropdown-item>
              <el-dropdown-item>Delete</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span>Tom</span>
      </div>
    </el-header>

    <el-main>
      <el-scrollbar>
        <el-table :data="tableData">
          <el-table-column prop="date" label="Date" width="140" />
          <el-table-column prop="name" label="Name" width="120" />
          <el-table-column prop="address" label="Address" />
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
