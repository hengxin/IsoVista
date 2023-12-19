<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-aside width="200px">
      <el-scrollbar>
        <h5 class="mb-2">DBTest</h5>
        <el-menu
            class="el-menu-vertical-demo"
            default-active="1"
        >
          <el-menu-item index="1" @click="this.$router.push('/dashboard')">
            <template #title>
              <el-icon>
                <Setting />
              </el-icon>
              <span>Dashboard</span>
            </template>
          </el-menu-item>
          <el-menu-item index="2" @click="this.$router.push('/bugs')">
            <template #title>
              <el-icon>
                <Failed/>
              </el-icon>
              <span>Bugs</span>
            </template>
          </el-menu-item>
          <el-menu-item index="3" @click="this.$router.push('/view')">
            <template #title>
              <el-icon>
                <View/>
              </el-icon>
              <span>View</span>
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
import {House, Failed, View, Setting} from '@element-plus/icons-vue'

</script>

<style scoped>
.el-menu-vertical-demo {
  height: 100vh;
}

.layout-container-demo .toolbar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  right: 20px;
}
</style>