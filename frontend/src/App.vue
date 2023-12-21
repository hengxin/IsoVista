<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <Sidebar />
    <router-view></router-view>
  </el-container>
</template>

<script lang="ts" setup>
import Sidebar from "@/components/Sidebar/Sidebar.vue";

</script>

<style scoped>

</style>