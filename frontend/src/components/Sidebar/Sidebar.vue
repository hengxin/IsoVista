<script setup>

import {Failed, List, Setting} from "@element-plus/icons-vue";

</script>

<template>
  <div class="Sidebar" >
    <el-aside width="200px">
      <el-scrollbar>
        <h1 class="logo">IsoVista</h1>
        <el-menu
            default-active="1"
        >
          <el-menu-item index="1" @click="$router.push('/configuration')">
            <template #title>
              <el-icon>
                <Setting />
              </el-icon>
              <span>Configuration</span>
            </template>
          </el-menu-item>
          <el-menu-item index="2" @click="$router.push('/runs')">
            <template #title>
              <el-icon>
                <List />
              </el-icon>
              <span>Runs</span>
            </template>
          </el-menu-item>
          <el-menu-item index="3" @click="$router.push('/bugs')">
            <template #title>
              <el-icon>
                <Failed />
              </el-icon>
              <span>Bugs</span>
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </el-aside>
  </div>


</template>

<style scoped>
.Sidebar {
  height: 100%;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  text-align: center;
}
</style>