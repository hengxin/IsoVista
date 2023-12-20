<script setup>
import {defineProps, onMounted} from 'vue'
import G6 from '@antv/g6';
import {get_graph} from "@/api/api"
const props = defineProps({
  bug_id: {
    type: String,
    required: true
  }
})

const data = {
  nodes: [],
  edges: []
}

onMounted(async () => {
  const tooltip = new G6.Tooltip({
    offsetX: 10,
    offsetY: 0,
    fixToNode: [1, 0],
    // the types of items that allow the tooltip show up
    itemTypes: ['node'],
    // custom the tooltip's content
    getContent: (e) => {
      const outDiv = document.createElement('div');
      outDiv.style.width = 'fit-content';
      //outDiv.style.padding = '0px 0px 20px 0px';
      outDiv.innerHTML = `
      <h4>Details</h4>
      <ul>
        <li>Type: ${e.item.getType()}</li>
      </ul>
      <ul>
        <li>Label: ${e.item.getModel().label || e.item.getModel().id}</li>
      </ul>
      <ul>
        <li>Ops: <br> ${e.item.getModel().ops.replace(/\n/g, "<br>")}</li>
      </ul>`;
      return outDiv;
    },
  });

  console.log(props.bug_id)

  await get_graph(props.bug_id).then(res => {
    console.log(res.data)
    data.nodes = res.data.nodes
    data.edges = res.data.edges
    console.log(data)
  })

  const container = document.getElementById('container');
  const width = container.scrollWidth;
  const height = container.scrollHeight || 500;

  const graph = new G6.Graph({
    container: 'container',
    width,
    height,
    // translate the graph to align the canvas's center, support by v3.5.1
    fitCenter: true,
    // the edges are linked to the center of source and target ndoes
    linkCenter: false,
    plugins: [tooltip],
    defaultNode: {
      type: 'circle',
      size: [40],
      color: '#5B8FF9',
      style: {
        fill: '#9EC9FF',
        lineWidth: 3,
      },
      labelCfg: {
        style: {
          fill: '#000',
          fontSize: 14,
        },
      },
    },
    defaultEdge: {
      type: 'quadratic',
      labelCfg: {
        autoRotate: true,
      },
      style: {
        stroke: 'gray',
        endArrow: true,
      }
    },
    modes: {
      default: ['drag-canvas', 'drag-node'],
    },
    nodeStateStyles: {
      // style configurations for hover state
      hover: {
        fillOpacity: 0.8,
      },
      // style configurations for selected state
      selected: {
        lineWidth: 5,
      },
    },
  });
  graph.data(data);
  graph.render();

  if (typeof window !== 'undefined')
    window.onresize = () => {
      if (!graph || graph.get('destroyed')) return;
      if (!container || !container.scrollWidth || !container.scrollHeight) return;
      graph.changeSize(container.scrollWidth, container.scrollHeight);
    };
});
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-main>
      <div id="container"></div>
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

.g6-component-tooltip {
  background-color: rgba(255, 255, 255, 0.8);
  padding: 0px 10px 24px 10px;
  box-shadow: rgb(174, 174, 174) 0px 0px 10px;
}
</style>