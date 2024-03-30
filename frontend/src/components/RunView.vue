<script setup>
import * as echarts from 'echarts';
import {defineProps, onMounted, onUnmounted, ref, watch} from "vue";
import {
  get_current_profile,
  get_current_run_id,
  get_current_runtime_info,
  get_run_profile,
  get_runtime_info,
} from "@/api/api.js";

const props = defineProps({
  run_id: {
    type: String,
    required: true
  }
})

let profileTimeChart;
let profileMemoryChart;
let profileStagesChart;
let runTimeCPUChart;
let runTimeMemoryChart;

let profileData = {}

const createProfileCharts = () => {
  const profileTimeChartDom = document.getElementById('profile-time');
  profileTimeChart = echarts.init(profileTimeChartDom);
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
    legend: {
      textStyle: {
        fontSize: 20
      },
      data: profileData.legend
    },
    xAxis: {
      name: 'name' in profileData ? profileData.name : '',
      nameLocation: 'middle',
      type: 'category',
      boundaryGap: false,
      data: profileData.x_axis,
      axisLabel: {
        fontSize: 20,
        color: '#000',
      },
      nameTextStyle: {
        lineHeight: 56,
        "fontSize": 22,
        color: '#000'
      }
    },
    yAxis: {
      name: 'Time(s)',
      type: 'value',
      axisLabel: {
        fontSize: 20,
        color: '#000'
      },
      nameTextStyle: {
        "fontSize": 22,
        color: '#000'
      }
    },
    series: profileData.timeSeries
  };
  profileTimeOption && profileTimeChart.setOption(profileTimeOption);

  const profileMemoryChartDom = document.getElementById('profile-memory');
  profileMemoryChart = echarts.init(profileMemoryChartDom);
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
    legend: {
      textStyle: {
        fontSize: 20
      },
      data: profileData.legend
    },
    xAxis: {
      name: profileData.name,
      nameLocation: 'middle',
      type: 'category',
      boundaryGap: false,
      data: profileData.x_axis,
      axisLabel: {
        fontSize: 20,
        color: '#000',
      },
      nameTextStyle: {
        lineHeight: 56,
        "fontSize": 22,
        color: '#000'
      }
    },
    yAxis: {
      name: 'Memory(MB)',
      type: 'value',
      axisLabel: {
        fontSize: 20,
        color: '#000'
      },
      nameTextStyle: {
        "fontSize": 22,
        color: '#000'
      }
    },
    series: profileData.memorySeries
  };
  profileMemoryOption && profileMemoryChart.setOption(profileMemoryOption);

  const profileStagesChartDom = document.getElementById('profile-stages');
  profileStagesChart = echarts.init(profileStagesChartDom);
  let profileStagesOption;

  profileStagesOption = {
    toolbox: {
      feature: {
        saveAsImage: {}
      }
    },
    tooltip: {
      trigger: "item",
      formatter: function (params) {
        console.log(params)
        return profileData.idxToChecker[params.dataIndex][params.componentIndex] + "<br/>" + params.marker + params.seriesName + ":&nbsp;&nbsp;<b>" + params.data + "</b>"
      }
    },
    legend: {
      textStyle: {
        fontSize: 20
      },
    },
    // grid: {
    //   left: '3%',
    //   right: '4%',
    //   bottom: '3%',
    //   containLabel: true
    // },
    xAxis: {
      name: profileData.name,
      nameLocation: 'middle',
      type: 'category',
      // boundaryGap: false,
      data: profileData.x_axis,
      axisLabel: {
        fontSize: 20,
        color: '#000',
      },
      nameTextStyle: {
        lineHeight: 56,
        "fontSize": 22,
        color: '#000'
      }
    },
    yAxis: {
      name: 'Time(s)',
      type: 'value',
      axisLabel: {
        fontSize: 20,
        color: '#000'
      },
      nameTextStyle: {
        "fontSize": 22,
        color: '#000'
      }
    },
    series: profileData.stageSeries,
  };

  profileStagesOption && profileStagesChart.setOption(profileStagesOption);
}

let runtimeInfoData = {};
const createRuntimeInfoCharts = () => {
  const runtimeCPUChartDom = document.getElementById('runtime-cpu');
  runTimeCPUChart = echarts.init(runtimeCPUChartDom);
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
      axisLabel: {
        formatter: '{HH}:{mm}:{ss}',
        fontSize: 20,
        color: '#000',
        rotate: 30
      },
    },
    yAxis: {
      name: 'CPU(%)',
      type: 'value',
      max: 100,
      axisLabel: {
        fontSize: 20,
        color: '#000'
      },
      nameTextStyle: {
        "fontSize": 22,
        color: '#000'
      }
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

  console.log(runTimeCPUChart)

  const runtimeMemoryChartDom = document.getElementById('runtime-memory');
  runTimeMemoryChart = echarts.init(runtimeMemoryChartDom);
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
      axisLabel: {
        formatter: '{HH}:{mm}:{ss}',
        fontSize: 20,
        color: '#000',
        rotate: 30
      },
    },
    yAxis: {
      name: 'Memory(MB)',
      type: 'value',
      axisLabel: {
        fontSize: 20,
        color: '#000'
      },
      nameTextStyle: {
        "fontSize": 22,
        color: '#000'
      }
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

let shouldRefresh = ref(false);
let currentRunId;
let refreshInterval;

const updateShouldRefresh = async () => {
  await get_current_run_id().then((res) => {
    console.log("current run_id: " + res.data)
    currentRunId = res.data
    shouldRefresh.value = (currentRunId !== null && currentRunId.toString() === props.run_id.toString())
    console.log("set shouldRefresh to " + shouldRefresh.value)
  })
}

const handleGetRuntimeInfoRes = (res)=> {
  runtimeInfoData = res.data
  runtimeInfoData.cpuUsage = []
  runtimeInfoData.memoryUsage = []
  for (let i = 0; i < runtimeInfoData.x_axis.length; i++) {
    runtimeInfoData.cpuUsage.push([runtimeInfoData.x_axis[i], runtimeInfoData.cpu[i]])
    runtimeInfoData.memoryUsage.push([runtimeInfoData.x_axis[i], (runtimeInfoData.memory[i] / 1024 / 1024).toFixed(2)])
  }
  console.log(runtimeInfoData)
}

const handleGetProfileRes = (res)=> {
  console.log(res.data)
  if (!res.data.series) {
    return
  }
  for (let data of res.data.series) {
    for (let i = 0; i < res.data.x_axis.length; i++) {
      data.time[i] = (data.time[i] / 1000).toFixed(2)
      data.memory[i] = (data.memory[i] / 1024).toFixed(2)
    }
    for (let field in data.stage_times) {
      // if (data.stage_times[field].every(element => element === 0)) {
      //   delete data.stage_times[field]
      //   continue
      // }
      for (let i = 0; i < res.data.x_axis.length; i++) {
        data.stage_times[field][i] = (data.stage_times[field][i] / 1000.0).toFixed(2)
      }
    }
  }
  profileData = res.data
  profileData.timeSeries = []
  profileData.memorySeries = []
  profileData.stageSeries = []
  profileData.legend = []
  profileData.idxToChecker = {}
  let maxIndex = {}
  for (let i = 0; i < res.data.x_axis.length; i++) {
    profileData.idxToChecker[i] = {}
    maxIndex[i] = 0
  }
  for (let [seriesIdx, series] of profileData.series.entries()) {
    console.log("series")
    console.log(seriesIdx, series)
    profileData.timeSeries.push({
      name: series.checker,
      data: series.time,
      type: 'line'
    })
    profileData.memorySeries.push({
      name: series.checker,
      data: series.memory,
      type: 'line'
    })
    console.log(maxIndex)
    Object.entries(series.stage_times).forEach(([stage, state_times], stageIdx) => {
      profileData.stageSeries.push({
        name: stage,
        type: 'bar',
        stack: series.checker,
        emphasis: {},
        data: state_times,
      })
      for (let [timeIdx, time] of state_times.entries()) {
        profileData.idxToChecker[timeIdx][maxIndex[timeIdx]] = series.checker
        maxIndex[timeIdx] += 1
      }
    })
    console.log(profileData.idxToChecker)

    profileData.legend.push(series.checker)
  }
  console.log(profileData)
}

const getData = async () => {
  await get_runtime_info(props.run_id).then((res) => {
    if (!res.data) {
      console.log("no runtime info data")
      return
    }
    console.log(res.data)
    handleGetRuntimeInfoRes(res)
  })
  await get_run_profile(props.run_id).then((res) => {
    if (!res.data) {
      console.log("no profile data")
      return
    }
    handleGetProfileRes(res)
  })
}

const setProfileData = ()=> {
  profileTimeChart.setOption({
    series: profileData.timeSeries,
    xAxis: {
      data: profileData.x_axis,
    },
    legend: {
      data: profileData.legend
    },
  })
  profileMemoryChart.setOption({
    series: profileData.memorySeries,
    xAxis: {
      data: profileData.x_axis,
    },
    legend: {
      data: profileData.legend
    },
  })
  profileStagesChart.setOption({
    series: profileData.stageSeries,
    xAxis: {
      data: profileData.x_axis,
    },
  })
}

const setRuntimeData = ()=> {
  runTimeCPUChart.setOption({
    series: {
      data: runtimeInfoData.cpuUsage,
    }
  })
  runTimeMemoryChart.setOption({
    series: {
      data: runtimeInfoData.memoryUsage
    }
  })
}

onMounted(async () => {
  await updateShouldRefresh()

  if (!shouldRefresh.value) {
    await getData()
  } else {
    console.log("should refresh")
    refreshInterval = setInterval(() => {
      get_current_runtime_info().then((res) => {
        if (!res.data) {
          console.log("no runtime info data")
          return
        }
        handleGetRuntimeInfoRes(res)
        setRuntimeData()
      })
      get_current_profile().then((res) => {
        if (!res.data) {
          console.log("no profile data")
          return
        }
        handleGetProfileRes(res)
        setProfileData()
      })
      updateShouldRefresh()
    }, 1000)
  }

  createProfileCharts()
  createRuntimeInfoCharts()
})

onUnmounted(() => {
  clearInterval(refreshInterval)
})

watch(shouldRefresh, async (newVal) => {
  if (!newVal) {
    console.log("stop refresh runtime info and profile")
    clearInterval(refreshInterval)
    await getData()
    setRuntimeData()
    setProfileData()
  }
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
          Statistics
        </span>
    </el-header>
    <el-main class="profile-container">
      <div id="profile-time" class="chart"></div>
      <div id="profile-memory" class="chart"></div>
      <div id="profile-stages" class="chart"></div>
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