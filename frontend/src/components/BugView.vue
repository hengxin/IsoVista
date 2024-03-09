<script setup>
import {defineProps, onMounted, ref} from 'vue'
import G6, {Algorithm} from '@antv/g6';
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
let bugName = ref("")
let removedNodes = new Map()
let removedEdges = new Map()

const {detectDirectedCycle} = Algorithm;

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
      let content = '';
      console.log(evt.item.getModel())
      if (evt.item.getStates().length && evt.item.getStates()[0] === 'marked') {
        content += '<div>Unmark</div>';
      } else {
        content += '<div>Mark</div>';
      }
      content += '<div>Delete</div>';
      if (evt.item._cfg.type === 'edge' && (evt.item.getModel().label.includes("CM")
          || evt.item.getModel().label.includes("WW") || evt.item.getModel().label.includes("RW"))) {
        if(evt.item.getModel().expanded) {
          content += '<div>Collapse</div>';
        } else {
          content += '<div>Expand</div>';
        }
      }
      return content;
    },
    handleMenuClick(target, item) {
      console.log(target.innerHTML)
      if (target.innerHTML === "Mark") {
        graph.setItemState(item, 'marked', true);
      } else if (target.innerHTML === "Unmark") {
        graph.setItemState(item, 'marked', false);
      } else if (target.innerHTML === "Delete") {
        graph.removeItem(item)
      } else if (target.innerHTML === "Collapse") {
        item.getModel().expanded = false
        removedNodes[item] = []
        removedEdges[item] = []
        // remove edge should before remove node, since remove nodes can automatically remove edges
        graph.getEdges()
            .filter((edge) => {
              return edge.getModel().relate_to.includes(item.getModel().id)
                  && !cycleEdgeList.some(cycleEdge => cycleEdge.source === edge.getSource().getID() && cycleEdge.target === edge.getTarget().getID())
            })
            .forEach((edge) => {
              removedEdges[item].push(JSON.parse(JSON.stringify(edge.getModel())))
              graph.removeItem(edge)
            })
        graph.getNodes()
            .filter((node) => {
              return node.getModel().relate_to.includes(item.getModel().id) && !cycleNodeList.includes(node.getID())
            })
            .forEach((node) => {
              removedNodes[item].push(JSON.parse(JSON.stringify(node.getModel())))
              graph.removeItem(node)
            })
      } else if (target.innerHTML === "Expand") {
        console.log(removedNodes[item])
        console.log(removedEdges[item])
        item.getModel().expanded = true
        removedNodes[item].forEach((node) => {
          graph.addItem("node", node)
        })
        removedEdges[item].forEach((edge) => {
          graph.addItem("edge", edge)
        })
      }
    },
  });

  const toolbar = new G6.ToolBar({
    position: {
      x: 230,
      y: 80
    }
  });

  await get_graph(props.bug_id).then(res => {
    console.log(res.data)
    bugName.value = res.data.name
    data.nodes = res.data.nodes
    data.edges = res.data.edges
    res.data.nodes.forEach(node => {
      node.label = node.label.replace(/Transaction/g, 'Txn')
    })
    res.data.edges.forEach(edge => {
      edge.expanded = false
      edge.style = {}
      if (edge.label.includes("WR")) {
        edge.style.stroke = "#5F95FF"
      } else if (edge.label.includes("SO")) {
        edge.style.stroke = "#61DDAA"
      } else if (edge.label.includes("WW")) {
        edge.style.stroke = "#F6903D"
        edge.expanded = true
      } else if (edge.label.includes("RW")) {
        edge.style.stroke = "#F6BD16"
        edge.expanded = true
      } else if (edge.label.includes("CM")) {
        edge.style.stroke = "#F08BB4"
        edge.expanded = true
      }
    })
  })

  const container = document.getElementById('container');
  const width = container.scrollWidth;
  const height = container.scrollHeight || 1100;

  const graph = new G6.Graph({
    container: 'container',
    width,
    height,
    // translate the graph to align the canvas's center, support by v3.5.1
    fitCenter: true,
    // the edges are linked to the center of source and target nodes
    linkCenter: true,
    plugins: [tooltip, menu, toolbar],
    enabledStack: true,
    layout: {
      type: 'force2',
      preventOverlap: true,
      linkDistance: 125,
      animate: false,
    },
    defaultNode: {
      type: 'circle',
      size: [66],
    },
    defaultEdge: {
      labelCfg: {
        autoRotate: true,
      },
      style: {
        endArrow: {
          path: G6.Arrow.vee(10, 10, 34),
          d: 34,
        }
      },
    },
    modes: {
      default: ['drag-canvas', 'drag-node'],
    },
    nodeStateStyles: {
      marked: {
        lineWidth: 3,
        fill: '#d6dff5',
      },
    },
    edgeStateStyles: {
      marked: {
        lineWidth: 3,
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

// highlight cycle
let cycle = detectDirectedCycle(data);
let cycleNodeList = []
let cycleEdgeList = []
for (let key in cycle) {
  cycleNodeList.push(key)
}
for (let i = 0; i < cycleNodeList.length - 1; i++) {
  cycleEdgeList.push({target: cycleNodeList[i], source: cycleNodeList[i + 1]})
}
cycleEdgeList.push({target: cycleNodeList[cycleNodeList.length - 1], source: cycleNodeList[0]})
console.log(cycleNodeList)
g.getNodes().filter(node => cycleNodeList.includes(node.getID())).forEach(node => {
  node.setState('marked', true)
})
console.log(cycleEdgeList)
g.getEdges().filter(edge => cycleEdgeList.some(cycleEdge => cycleEdge.source === edge.getSource().getID() && cycleEdge.target === edge.getTarget().getID())).forEach(edge => {
  console.log(edge)
  edge.setState('marked', true)
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
    <el-header>
      <h2>{{ bugName }}
      </h2>
    </el-header>
    <el-main>
      <el-container id="toolbar">
        <el-dropdown trigger="click">
          <span class="el-dropdown-link">
            Download
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleDownloadPNG">PNG</el-dropdown-item>
              <el-dropdown-item @click="handleDownloadTikz">TikZ</el-dropdown-item>
              <el-dropdown-item @click="handleDownloadDot">DOT</el-dropdown-item>
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
  text-align: center;
  //background-color: var(--el-color-primary-light-7);
  //color: var(--el-text-color-primary);
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
  margin-top: 70px;
}
</style>