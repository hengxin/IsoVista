<script lang="ts" setup>
import {reactive, ref} from "vue";
import {ElLoading, ElMessage, ElMessageBox} from "element-plus";
import type {UploadProps, UploadUserFile} from 'element-plus'
import {run} from "@/api/api";

const testingOption = reactive({
  db_url: 'jdbc:mysql://localhost:3306',
  db_type: 'MYSQL',
  db_isolation: 'SERIALIZABLE',
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
  {label: 'Read_Uncommitted', value: 'READ_UNCOMMITTED'},
  {label: 'Read_Committed', value: 'READ_COMMITTED'},
  {label: 'Repeatable_Read', value: 'REPEATABLE_READ'},
  {label: 'Serializable', value: 'SERIALIZABLE'},
  {label: 'Transaction_Snapshot', value: 'TRANSACTION_SNAPSHOT'},
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
  const loading = ElLoading.service({
    lock: true,
    text: 'Loading',
    background: 'rgba(0, 0, 0, 0.7)',
  })
  run(testingOption).then(res => {
    console.log(res)
    loading.close()
    ElMessage({
      message: 'Run success',
      type: 'success',
    })
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
</script>

<template>
  <el-container class="layout-container-demo">
    <el-header></el-header>

    <el-main>
      <el-row>
        <el-col :span="2">
        </el-col>
        <el-col :span="9">
          <el-form label-position="left"
                   label-width="220px"
                   class="form-style"
          >
            <header class="form-header">
              <h2>Database Settings</h2>
            </header>
            <el-form-item label="DB URL">
              <el-input v-model="testingOption.db_url" placeholder="JDBC URL" clearable class="fixed-width"></el-input>
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
        </el-col>
        <el-col :span="2">
        </el-col>
        <el-col :span="9">
          <el-form label-position="left"
                   label-width="220px"
                   class="form-style"
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
              <el-select v-model="testingOption.workload_distribution" placeholder="" @change="handleSelectionChange"
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
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="2">
        </el-col>
        <el-col :span="9">

          <el-form  label-position="left"
                    label-width="220px"
                    class="form-style">
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
              <el-button type="primary" @click="handleSubmit">start</el-button>
            </el-form-item>
            <el-divider></el-divider>
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
        </el-col>
      </el-row>
    </el-main>
  </el-container>
</template>

<style>

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
</style>