<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-aside width="200px">
      <el-scrollbar>
        <el-menu>
          <el-menu-item index="1" @click="this.$router.push('/dashboard')">
            <template #title>
              <el-icon><House /></el-icon>Dashboard
            </template>
          </el-menu-item>
          <el-menu-item index="2" @click="this.$router.push('/bugs')">
            <template #title>
              <el-icon><Failed /></el-icon>Bugs
            </template>
          </el-menu-item>
          <el-menu-item index="3" @click="this.$router.push('/view')">
            <template #title>
              <el-icon><View /></el-icon>View
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container>
      <router-view></router-view>
    </el-container>

  </el-container>
</template>

<script lang="ts" setup>
import { House, Failed, View } from '@element-plus/icons-vue'
</script>

<style scoped>

.layout-container-demo .el-aside {
  color: var(--el-text-color-primary);
  background: var(--el-color-primary-light-8);
}
.layout-container-demo .el-menu {
  border-right: none;
}
.layout-container-demo .toolbar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  right: 20px;
}
</style>