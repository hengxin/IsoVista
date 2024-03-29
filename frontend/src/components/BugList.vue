<script setup>
import {onMounted, ref} from "vue"
import {download_bug, get_bug_list, change_bug_tag} from "@/api/api.js";
import { useRouter } from 'vue-router'

const router = useRouter()

const tableData = ref([])

async function get_bugs() {
  get_bug_list().then(res => {
    console.log(res.data)
    for (let i = 0; i < res.data.length; i++) {
      tableData.value.push({
        id: res.data[i].bug_id,
        tagName: res.data[i].tag_name,
        tagType: res.data[i].tag_type,
        dbType: res.data[i].db_type,
        dbIsolation: res.data[i].db_isolation,
        filename: res.data[i].bug_dir,
        checkerIsolation: res.data[i].checker_isolation.replace('[', '').replace(']', '').replace(/'/g, ""),
        date: res.data[i].timestamp
      })
    }
    console.log(tableData)
  })
}

function handleView(row){
  router.push("/bug_view/" + row.id)
}

function handleDownload(row) {
  console.log(row.id)
  download_bug(row.id).then(res => {

    const { data, headers } = res
    const fileName = decodeURIComponent(escape(res.headers['file-name']))

    //const blob = new Blob([JSON.stringify(data)], ...)
    const blob = new Blob([data], {type: headers['content-type']})
    let dom = document.createElement('a')
    let url = window.URL.createObjectURL(blob)
    dom.href = url
    dom.download = decodeURI(fileName)
    dom.style.display = 'none'
    document.body.appendChild(dom)
    dom.click()
    dom.parentNode.removeChild(dom)
    window.URL.revokeObjectURL(url)
  })
}

onMounted(async () => {
  await get_bugs()
})

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleDateString(); // 或者使用其他格式化库
}

const tagNameType = [
  { name: "Fixed", type: "success"},
  { name: "Minor", type: ""},
  { name: "Normal", type: "warning"},
  { name: "Critical", type: "danger"}
]

const handleChangeTag = (row, tagName, tagType) => {
  console.log(row, tagName, tagType)
  change_bug_tag(row.id, tagName, tagType).then(res => {
    row.tagName = tagName
    row.tagType = tagType
  })
}
</script>

<template>
  <el-container class="layout-container-demo" style="height: 100%">
    <el-header>
      <span class="info">
        All Bugs
      </span>
    </el-header>

    <el-main>
      <el-scrollbar>
        <el-table :data="tableData"
                  :default-sort="{ prop: 'id', order: 'ascending' }"
                  stripe
                  >
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="dbType" label="DB Type" width="150"/>
          <el-table-column prop="dbIsolation" label="DB Isolation Level" width="300"/>
          <el-table-column prop="checkerIsolation" label="Checker Isolation Level" width="280"/>
          <el-table-column prop="date" label="Date" width="200">
            <template #default="{ row }">
              {{ formatDate(row.date) }}
            </template>
          </el-table-column>
          <el-table-column label="Tag" width="120">
            <template #default="scope">
              <el-dropdown>
                <el-tag
                        v-if="scope.row.tagName"
                        :type="scope.row.tagType"
                >{{scope.row.tagName}}
                </el-tag>
                <el-button v-else class="button-new-tag" size="small">
                  + New Tag
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="scope.row.tagName" @click="handleChangeTag(scope.row, '','')">
                      <el-tag type="info">
                        None
                      </el-tag>
                    </el-dropdown-item>
                    <el-dropdown-item v-for="tag in tagNameType" :key="tag" @click="handleChangeTag(scope.row, tag.name, tag.type)">
                      <el-tag :type="tag.type" size="large">
                        {{tag.name}}
                      </el-tag>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
          <el-table-column label="Operations" width="200">
            <template #default="scope">
              <el-button
                  link
                  type="primary"
                  @click="handleView(scope.row)"
              >View</el-button
              >
              <el-button
                  link
                  type="primary"
                  @click="handleDownload(scope.row)"
              >Download</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </el-scrollbar>
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-container-demo .el-header {
  position: relative;
  color: var(--el-text-color-primary);
  margin-bottom: 20px;
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

.info {
  font-size: 28px;
  display: inline-block;
  margin-top: 20px;
  font-weight: bold;
}
</style>
