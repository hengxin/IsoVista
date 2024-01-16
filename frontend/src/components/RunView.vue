<script setup>
import * as echarts from 'echarts';
import {defineProps, onMounted} from "vue";
import {get_run_profile, get_runtime_info} from "@/api/api.js";

const props = defineProps({
  run_id: {
    type: String,
    required: true
  }
})

let profileData = null

const createProfileCharts = () => {
  if (profileData == null) {
    return
  }
  const profileTimeChartDom = document.getElementById('profile-time');
  const profileTimeChart = echarts.init(profileTimeChartDom);
  let profileTimeOption;

  profileTimeOption = {
    toolbox: {
      feature: {
        magicType: { type: ['line', 'bar'] },
        saveAsImage: {}
      }
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      name: profileData.name,
      type: 'category',
      boundaryGap: false,
      data: profileData.x_axis,
    },
    yAxis: {
      name: 'Time(s)',
      type: 'value'
    },
    series: [
      {
        data: profileData.time,
        type: 'line'
      }
    ]
  };
  profileTimeOption && profileTimeChart.setOption(profileTimeOption);

  const profileMemoryChartDom = document.getElementById('profile-memory');
  const profileMemoryChart = echarts.init(profileMemoryChartDom);
  let profileMemoryOption;

  profileMemoryOption = {
    toolbox: {
      feature: {
        magicType: { type: ['line', 'bar'] },
        saveAsImage: {}
      }
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      name: profileData.name,
      type: 'category',
      boundaryGap: false,
      data: profileData.x_axis,
    },
    yAxis: {
      name: 'Memory(MB)',
      type: 'value'
    },
    series: [
      {
        data: profileData.memory,
        type: 'line'
      }
    ]
  };
  profileMemoryOption && profileMemoryChart.setOption(profileMemoryOption);
}

let runtimeInfoData = null
const createRuntimeInfoCharts = () => {
  if (runtimeInfoData == null) {
    return
  }
  const runtimeCPUChartDom = document.getElementById('runtime-cpu');
  const runTimeCPUChart = echarts.init(runtimeCPUChartDom);
  let runtimeCPUCOption;

  runtimeCPUCOption = {
    toolbox: {
      feature: {
        saveAsImage: {}
      }
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'time',
    },
    yAxis: {
      name: 'CPU(%)',
      type: 'value',
      max: 100
    },
    series: [
      {
        data: runtimeInfoData.cpuUsage,
        type: 'line',
        smooth: true,
        showSymbol: false
      }
    ]
  };
  runtimeCPUCOption && runTimeCPUChart.setOption(runtimeCPUCOption);

  const runtimeMemoryChartDom = document.getElementById('runtime-memory');
  const runTimeMemoryChart = echarts.init(runtimeMemoryChartDom);
  let runtimeMemoryCOption;

  runtimeMemoryCOption = {
    toolbox: {
      feature: {
        saveAsImage: {}
      }
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      // name: runtimeInfoData.name,
      type: 'time',
    },
    yAxis: {
      name: 'Memory(MB)',
      type: 'value',
    },
    series: [
      {
        data: runtimeInfoData.memoryUsage,
        type: 'line',
        smooth: true,
        showSymbol: false
      }
    ]
  };
  runtimeMemoryCOption && runTimeMemoryChart.setOption(runtimeMemoryCOption);
}

onMounted(async () => {
  await get_run_profile(props.run_id).then((res) => {
    console.log(res.data)
    if (!res.data) {
      console.log("no profile data")
      return
    }
    profileData = res.data
    for (let i = 0; i < profileData.x_axis.length; i++) {
      profileData.time[i] = (profileData.time[i] / 1000).toFixed(2)
      profileData.memory[i] = (profileData.memory[i] / 1024).toFixed(2)
    }
    console.log(profileData)
  })


  await get_runtime_info(props.run_id).then((res) => {
    if (!res.data) {
      console.log("no runtime info data")
      return
    }
    runtimeInfoData = res.data
    runtimeInfoData.cpuUsage = []
    runtimeInfoData.memoryUsage = []
    for (let i = 0; i < runtimeInfoData.x_axis.length; i++) {
      runtimeInfoData.cpuUsage.push([runtimeInfoData.x_axis[i], runtimeInfoData.cpu[i]])
      runtimeInfoData.memoryUsage.push([runtimeInfoData.x_axis[i], (runtimeInfoData.memory[i] / 1024 / 1024).toFixed(2)])
    }
    console.log(runtimeInfoData)
  })

  createProfileCharts()
  createRuntimeInfoCharts()

})


</script>

<template>
  <el-container>
    <el-header>
      <span class="info">
        Runtime Info
      </span>
    </el-header>
    <el-main class="runtime-info-container">
      <div id="runtime-cpu" class="chart"></div>
      <div id="runtime-memory" class="chart"></div>
    </el-main>
    <el-header>
        <span class="info">
          Profile
        </span>
    </el-header>
    <el-main class="profile-container">

      <div id="profile-time" class="chart"></div>
      <div id="profile-memory" class="chart"></div>
    </el-main>
  </el-container>
</template>

<style scoped>
.runtime-info-container {
  display: flex;
}

.profile-container {
  display: flex;
}

.chart {
  width: 800px;
  height: 500px;
}

.info {
  font-size: 28px;
  display: inline-block;
  margin-top: 20px;
  font-weight: bold;
}

</style>