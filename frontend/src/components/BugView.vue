<script setup>
import {defineProps, onMounted, ref} from 'vue'
import G6 from '@antv/g6';
import {get_graph} from "@/api/api"
import {insertCss} from 'insert-css'
insertCss(`
  .g6-component-contextmenu {
    padding: 0
  }
  .g6-component-contextmenu ul {
    margin: 0;
    text-align: center;
  }
  .g6-component-contextmenu div {
    padding: 6px 20px;
    cursor: pointer;
  }
  .g6-component-contextmenu div:hover {
    background: #b4b4b4;
  }
`)

const props = defineProps({
  bug_id: {
    type: String,
    required: true
  }
})

let data = {
  nodes: [],
  edges: []
}

let g = ref(G6.Graph);
const handleDownloadPNG = () => {
  console.log("download png")
  g.downloadFullImage('image', 'image/png')
}

const downloadStringAsFile = (str, fileName) => {
  var blob = new Blob([str], {type: 'text/plain'});
  var url = URL.createObjectURL(blob);
  var link = document.createElement('a');
  link.href = url;
  link.download = fileName;

  link.click();
  URL.revokeObjectURL(url);
}

const handleDownloadTikz = () => {
  var tikzCode = "\\documentclass[tikz]{standalone}\n";
  tikzCode += "\\begin{document}\n";
  tikzCode += "\\begin{tikzpicture}\n";

  data.nodes.forEach(function(node) {
    console.log(node)
    tikzCode += "\\node[draw] at (" + node.x / 25 + "," + -node.y / 25 + ") (" + node.id.match(/\d+/g) + ") {" + node.label + "};\n";
  });

  data.edges.forEach(function(edge) {
    tikzCode += "\\draw[->] (" + edge.source.match(/\d+/g) + ") to node[above] {$" + edge.label + "$} (" + edge.target.match(/\d+/g) + ");\n";
  });

  tikzCode += "\\end{tikzpicture}\n";
  tikzCode += "\\end{document}\n";
  console.log(tikzCode)

  downloadStringAsFile(tikzCode, "tikz.tex")
}

const handleDownloadDot = () => {
  var dotCode = "digraph {\n";
  data.nodes.forEach(function(node) {
    dotCode += "  \"" + node.id + "\" [label=\"" + node.label + "\"];\n";
  });
  data.edges.forEach(function(edge) {
    dotCode += "  \"" + edge.source + "\" -> \"" + edge.target + "\" [label=\"" + edge.label + "\"];\n";
  });
  dotCode += "}\n";

  downloadStringAsFile(dotCode, "graph.dot")
}

onMounted(async () => {
  const tooltip = new G6.Tooltip({
    offsetX: 10,
    offsetY: 0,
    fixToNode: [1, 0],
    // the types of items that allow the tooltip show up
    itemTypes: ['node'],
    // trigger: 'click',
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

  const menu = new G6.Menu({
    getContent(evt) {
      if (evt.item.getStates().length && evt.item.getStates()[0] === 'highlight') {
        return `<div>Unmark</div>
              <div>Delete</div>`;
      } else {
        return `<div>Mark</div>
              <div>Delete</div>`;
      }
    },
    handleMenuClick(target, item) {
      console.log(target.innerHTML)
      if (target.innerHTML === "Mark") {
        graph.setItemState(item, 'highlight', true);
      } else if (target.innerHTML === "Unmark") {
        graph.setItemState(item, 'highlight', false);
      } else if (target.innerHTML === "Delete") {
        if (item._cfg.type === 'node') {
          data.nodes = data.nodes.filter(n => n.id !== item._cfg.id)
          data.edges = data.edges.filter(e => e.source !== item._cfg.id && e.target !== item._cfg.id)
        } else if (item._cfg.type === 'edge') {
          data.edges = data.edges.filter(e => e.id !== item._cfg.id)
        }
        graph.changeData(data);
      }
    },
  });

  const toolbar = new G6.ToolBar({
    position: {
      x: 230,
      y: 30
    }
  });

  await get_graph(props.bug_id).then(res => {
    console.log(res.data)
    data.nodes = res.data.nodes
    data.edges = res.data.edges
    res.data.nodes.forEach(node => {
      node.label = node.label.replace(/Transaction/g, 'Txn')
    })
    console.log(data)
  })

  const container = document.getElementById('container');
  const width = container.scrollWidth;
  const height = container.scrollHeight || 1000;

  const graph = new G6.Graph({
    container: 'container',
    width,
    height,
    // translate the graph to align the canvas's center, support by v3.5.1
    fitCenter: true,
    // the edges are linked to the center of source and target ndoes
    linkCenter: false,
    plugins: [tooltip, menu, toolbar],
    enabledStack: true,
    layout: {
      type: 'fruchterman',
      gravity: 3,
      speed: 10,
      // for rendering after each iteration
      tick: () => {
        graph.refreshPositions()
      }
    },
    animate: true,
    defaultNode: {
      type: 'circle',
      size: [65],
      // color: '#5B8FF9',
      // style: {
      //   fill: '#9EC9FF',
      //   lineWidth: 3,
      // },
      // labelCfg: {
      //   style: {
      //     fill: '#000',
      //     fontSize: 14,
      //   },
      // },
    },
    defaultEdge: {
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

  G6.Util.processParallelEdges(data.edges, 20, 'quadratic', 'line', 'loop');
  graph.data(data);
  graph.render();
  g = graph

  graph.on('node:click', e => {
    console.log(e)
  })

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
      <el-container id="toolbar">
        <el-dropdown trigger="click">
          <span class="el-dropdown-link">
            Download
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleDownloadPNG">PNG</el-dropdown-item>
              <el-dropdown-item @click="handleDownloadTikz">Tikz</el-dropdown-item>
              <el-dropdown-item @click="handleDownloadDot">Dot</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-container>
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

#toolbar {
  position: absolute;
  top: 20px;
  right: 20px;
}

#container .g6-component-contextmenu {
  background-color: rgba(255, 255, 255, 0.8);
  padding: 0px 10px 24px 10px;
  box-shadow: rgb(174, 174, 174) 0px 0px 10px;
}

.el-dropdown-link {
  cursor: pointer;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
}
</style>