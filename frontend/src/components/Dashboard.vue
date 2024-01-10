<script lang="ts" setup>
import {reactive, ref} from "vue"
import {ElMessage, ElMessageBox} from "element-plus"
import type {UploadProps, UploadUserFile} from "element-plus"
import {run, get_current_log} from "@/api/api"

const testingOption = reactive({
  db_url: 'jdbc:mysql://localhost:3306',
  db_type: 'MYSQL',
  db_isolation: 'TRANSACTION_SERIALIZATION',
  db_username: 'dbtest',
  db_password: 'dbtest_pwd',
  workload_type: 'general',
  workload_history: 10,
  workload_session: 20,
  workload_transaction: 100,
  workload_operation: 5,
  workload_key: 1000,
  workload_readproportion: 0.5,
  workload_distribution: 'UNIFORM',
  checker_type: 'PolySI',
  checker_isolation: 'SNAPSHOT_ISOLATION',
  profiler_enable: false
});

const dbOptions = [
  {label: 'MySQL', value: 'MYSQL'},
  {label: 'PostgreSQL', value: 'POSTGRES'},
  {label: 'H2', value: 'H2'}
];
const dbIsolationLevelOptions = [
  {label: 'Read_Uncommitted', value: 'TRANSACTION_READ_UNCOMMITTED'},
  {label: 'Read_Committed', value: 'TRANSACTION_READ_COMMITTED'},
  {label: 'Repeatable_Read', value: 'TRANSACTION_REPEATABLE_READ'},
  {label: 'Serializable', value: 'TRANSACTION_SERIALIZATION'},
];
const distributionOptions = [
  {label: 'Uniform', value: 'uniform'},
  {label: 'Zipf', value: 'zipf'},
  {label: 'Hotspot', value: 'hotspot'},
];
const checkerOptions = [
  {label: 'PolySI', value: 'PolySI'},
  {label: 'C4', value: 'C4'}
];
const checkerIsolationLevelOptions = [
  {label: 'Read_Committed', value: 'READ_COMMITTED'},
  {label: 'Read_Atomicity', value: 'READ_ATOMICITY'},
  {label: 'Causal_Consistency', value: 'CAUSAL_CONSISTENCY'},
  {label: 'Snapshot_Isolation', value: 'SNAPSHOT_ISOLATION'},
];
const handleSelectionChange = (value: string) => {
  console.log('Selected:', value);
};

const handleSwitch = (value: boolean) => {
  console.log('Enabled: ', value);
};


async function handleSubmit() {
  console.log(testingOption)
  dialogVisible.value = true
  const intervalRefreshLog = setInterval(() => {
    get_current_log().then(
        res => {
          currentLog.value = res.data
          // console.log(currentLog.value)
        }
    )
  }, 500);
  run(testingOption).then(res => {
    console.log(res)
    ElMessage({
      message: 'Run success',
      type: 'success',
    })
  }).finally(() => {
    dialogVisible.value = false
    clearInterval(intervalRefreshLog)
  })
}

const active = ref(0)
const next = () => {
  if (active.value++ > 2) active.value = 0
}
const back = () => {
  if (active.value-- < 0) active.value = 2
}

const fileList = ref<UploadUserFile[]>([])
const handleRemove: UploadProps['onRemove'] = (file, uploadFiles) => {
  console.log(file, uploadFiles)
}

const handlePreview: UploadProps['onPreview'] = (uploadFile) => {
  console.log(uploadFile)
}

const handleExceed: UploadProps['onExceed'] = (files, uploadFiles) => {
  ElMessage.warning(
      `The limit is 3, you selected ${files.length} files this time, add up to ${
          files.length + uploadFiles.length
      } totally`
  )
}

const beforeRemove: UploadProps['beforeRemove'] = (uploadFile, uploadFiles) => {
  return ElMessageBox.confirm(
      `Cancel the transfer of ${uploadFile.name} ?`
  ).then(
      () => true,
      () => false
  )
}

const dialogVisible = ref(false)
const currentLog = ref('')
const activeIndex = ref(0)
const handleIndexChange = (index) => {
  activeIndex.value = index
}
</script>

<template>
  <el-container class="layout-container-demo">
    <el-header></el-header>

    <el-main>
      <el-row :gutter="20">
        <el-col :span="16">
          <el-carousel trigger="click"
                       type="card"
                       :autoplay="false"
                       height="900px"
                       direction="vertical"
                       arrow="always"
                       @change="handleIndexChange">
            <el-carousel-item>
              <el-form label-position="left"
                       label-width="200px"
                       class="el-form-in-carousel"
              >
                <header class="form-header">
                  <h2>Database Settings</h2>
                </header>
                <el-form-item label="DB URL">
                  <el-input v-model="testingOption.db_url" placeholder="JDBC URL" clearable
                            class="fixed-width"></el-input>
                </el-form-item>
                <el-form-item label="DB Type">
                  <el-select v-model="testingOption.db_type" placeholder="" @change="handleSelectionChange"
                             class="fixed-width">
                    <el-option
                        v-for="option in dbOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value">
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="DB Isolation Level">
                  <el-select v-model="testingOption.db_isolation" placeholder="" @change="handleSelectionChange"
                             class="fixed-width">
                    <el-option
                        v-for="option in dbIsolationLevelOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value">
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="Username">
                  <el-input v-model="testingOption.db_username" placeholder="DB username" clearable
                            class="fixed-width"></el-input>
                </el-form-item>
                <el-form-item label="Password">
                  <el-input v-model="testingOption.db_password" placeholder="DB password" clearable
                            class="fixed-width"></el-input>
                </el-form-item>
              </el-form>
            </el-carousel-item>
            <el-carousel-item>
              <el-form label-position="left"
                       label-width="220px"
                       class="el-form-in-carousel"
              >
                <header class="form-header">
                  <h2>Workload Settings</h2>
                </header>
                <el-form-item label="#History">
                  <el-input-number
                      v-model="testingOption.workload_history"
                      :min="0"
                      :max="99"
                      controls-position="right"
                      class="fixed-width"
                  />
                </el-form-item>
                <el-form-item label="#Session">
                  <el-input-number
                      v-model="testingOption.workload_session"
                      :min="0"
                      :max="999"
                      controls-position="right"
                      class="fixed-width"
                  />
                </el-form-item>
                <el-form-item label="#Txn/Sess">
                  <el-input-number
                      v-model="testingOption.workload_transaction"
                      :min="0"
                      :max="999"
                      controls-position="right"
                      class="fixed-width"
                  />
                </el-form-item>
                <el-form-item label="#Op/Txn">
                  <el-input-number
                      v-model="testingOption.workload_operation"
                      :min="0"
                      :max="999"
                      controls-position="right"
                      class="fixed-width"
                  />
                </el-form-item>
                <el-form-item label="#Key">
                  <el-input-number
                      v-model="testingOption.workload_key"
                      :min="0"
                      :max="99999999"
                      controls-position="right"
                      class="fixed-width"
                  />
                </el-form-item>
                <el-form-item label="Read Proportion">
                  <el-input-number
                      v-model="testingOption.workload_readproportion"
                      :min="-1"
                      :max="0"
                      controls-position="right"
                      class="fixed-width"
                  />
                </el-form-item>
                <el-form-item label="Distribution">
                  <el-select v-model="testingOption.workload_distribution" placeholder=""
                             @change="handleSelectionChange"
                             class="fixed-width">
                    <el-option
                        v-for="option in distributionOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value">
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-form>
            </el-carousel-item>
            <el-carousel-item>
              <el-form label-position="left"
                       label-width="220px"
                       class="el-form-in-carousel">
                <header class="form-header">
                  <h2>Checker Settings</h2>
                </header>
                <el-form-item label="Checker Type">
                  <el-select v-model="testingOption.checker_type" placeholder="" @change="handleSelectionChange"
                             class="fixed-width">
                    <el-option
                        v-for="option in checkerOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value">
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="Checker Isolation Level">
                  <el-select v-model="testingOption.checker_isolation" placeholder=""
                             @change="handleSelectionChange"
                             class="fixed-width">
                    <el-option
                        v-for="option in checkerIsolationLevelOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value">
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-divider/>
                <el-form-item label="Enable Profiler">
                  <el-switch
                      v-model="testingOption.profiler_enable"
                      class="ml-2"
                      @change="handleSwitch"
                  />
                </el-form-item>
                <el-form-item>
                </el-form-item>
                <el-form-item>
                  <el-upload
                      v-model:file-list="fileList"
                      class="upload-demo"
                      action=""
                      multiple
                      :on-preview="handlePreview"
                      :on-remove="handleRemove"
                      :before-remove="beforeRemove"
                      :limit="3"
                      :on-exceed="handleExceed"
                  >
                    <el-button type="primary">Upload</el-button>
                    <template #tip>
                      <div class="el-upload__tip">
                        upload your generated history file here
                      </div>
                    </template>
                  </el-upload>
                </el-form-item>
              </el-form>
            </el-carousel-item>
          </el-carousel>
        </el-col>
        <el-col :span="8">
          <el-card class="box-card" v-if="activeIndex===0">
            <template #header>
              <div class="card-header">
                <span>Database Setting Details</span>
              </div>
              <p> url: your jdbc url to connect to the database</p>
              <p> username: the username of the database</p>
              <p> password: the password of the database</p>
              <p> isolation level: stub, currently all history are generated under serialization </p>
              <p> database type: support MYSQL, POSTGRES and H2</p>
            </template>
          </el-card>
          <el-card class="box-card" v-if="activeIndex===1">
            <template #header>
              <div class="card-header">
                <span>Workload Setting Details</span>
              </div>
            </template>
          </el-card>
          <el-card class="box-card" v-if="activeIndex===2">
            <template #header>
              <div class="card-header">
                <span>Checker Setting Details</span>
              </div>

            </template>
          </el-card>
        </el-col>
      </el-row>
      <el-button type="primary" @click="handleSubmit();">start</el-button>
      <el-dialog class="current-log" v-model="dialogVisible" title="Runtime logs">
        <pre>{{currentLog}}</pre>
      </el-dialog>
    </el-main>
  </el-container>
</template>

<style scoped>

.fixed-width {
  width: 600px;
}

.form-style {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 15px;
  padding-left: 20px;
  padding-right: 20px;
  background-color: rgba(24, 67, 237, 0.1);
  position: relative;
}

.form-header h2 {
  margin-bottom: 20px;
  text-align: left;
}


.el-carousel {
  width: 100%;
  margin-bottom: 20px;
}

.el-carousel__container {
  display: flex;
  align-items: center;
}

.el-form-in-carousel {
  max-width: 100%;
  max-height: 100%;
  border: 1px solid transparent;
  border-radius: 15px;
  padding-left: 20px;
  padding-right: 20px;
  background-color: rgb(236, 236, 236);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text {
  font-size: 14px;
}

.item {
  margin-bottom: 18px;
}

.current-log {
  overflow: auto;
}
.current-log pre {
  white-space: pre-wrap;
  word-wrap: break-word;
}

.box-card {
}

</style>