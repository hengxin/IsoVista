<script lang="ts" setup>
import {reactive, ref} from "vue"
import {ElMessage, ElMessageBox} from "element-plus"
import type {UploadProps, UploadUserFile} from "element-plus"
import {run, get_current_log} from "@/api/api"
import {InfoFilled} from "@element-plus/icons-vue"

const testingOption = reactive({
  db_url: 'jdbc:mysql://localhost:3306',
  db_type: 'MYSQL',
  db_isolation: 'TRANSACTION_SERIALIZATION',
  db_username: 'dbtest',
  db_password: 'dbtest_pwd',
  workload_type: 'general',
  workload_history: 1,
  workload_session: '[5,10,20,30]',
  workload_transaction: 100,
  workload_operation: 5,
  workload_key: 1000,
  workload_readproportion: 0.5,
  workload_variable: '',
  workload_distribution: 'UNIFORM',
  checker_isolation: 'SNAPSHOT_ISOLATION',
  profiler_enable: true
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
  for (const key in testingOption) {
    if (!testingOption.hasOwnProperty(key) || !key.startsWith("workload_")) {
      continue;
    }
    if (typeof testingOption[key] === "string" && testingOption[key].startsWith("[")) {
      testingOption.workload_variable = key.replace('workload_', '')
    }
  }
  console.log(testingOption)
  run(testingOption).then(res => {
    console.log(res)
    ElMessage({
      message: 'Add to run queue successfully',
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
                       height="500px"
                       arrow="always"
                       @change="handleIndexChange">
            <el-carousel-item class="el-carousel-item-demo">
              <el-form label-position="left"
                       label-width="33%"
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
                  </el-select>&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> The database type you want to test. Currently we support MySQL, PostgreSQL and H2. </template>
                    <el-icon color="blue">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
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
            <el-carousel-item class="el-carousel-item-demo">
              <el-form label-position="left"
                       label-width="33%"
              >
                <header class="form-header">
                  <h2>Workload Settings</h2>
                </header>
                <el-form-item label="#History">
                  <el-input
                      v-model="testingOption.workload_history"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> historical number of transactions  </template>
                    <el-icon color="blue">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Session">
                  <el-input
                      v-model="testingOption.workload_session"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> number of simulated sessions  </template>
                    <el-icon color="blue">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Txn/Sess">
                  <el-input
                      v-model="testingOption.workload_transaction"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                  <template #content>number of transactions in each session</template>
                  <el-icon color="blue">
                    <InfoFilled/>
                  </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Op/Txn">
                  <el-input
                      v-model="testingOption.workload_operation"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                  <template #content> number of operations in each transaction </template>
                  <el-icon color="blue">
                    <InfoFilled/>
                  </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Key">
                  <el-input
                      v-model="testingOption.workload_key"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> number of workload keys </template>
                    <el-icon color="blue">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="Read Proportion">
                  <el-input
                      v-model="testingOption.workload_readproportion"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> The proportion of read operations in each transaction </template>
                    <el-icon color="blue">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
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
                  </el-select>&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> The distribution type of transaction access keys. We support uniform, zipfan and hotspot</template>
                    <el-icon color="blue">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-form>
            </el-carousel-item>
            <el-carousel-item class="el-carousel-item-demo">
              <el-form label-position="left"
                       label-width="35%"
                       >
                <header class="form-header">
                  <h2>Checker Settings</h2>
                </header>
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
            </template>
          </el-card>
          <el-card class="box-card" v-if="activeIndex===1">
            <template #header>
              <div class="card-header">
                <span>Workload Details</span>
              </div>
            </template>
            <p>If you want to upload your own history file, please make sure the format is correct.</p>
            <p>opType(key, value, session, txnID) </p>
            <p>For example:</p>
            <p>r(4,34,0,0)</p>
            <p> w(6,1,0,0)</p>
            <p> r(9,15,0,0)</p>
            <p> w(8,1,0,0)</p>
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
    </el-main>
  </el-container>
</template>

<style scoped>

.fixed-width {
  width: 90%;
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

.box-card {
}

.el-carousel-item-demo {
  border: 1px solid transparent;
  border-radius: 15px;
  padding-left: 10px;
  padding-right: 5px;
  background-color: rgb(236, 236, 236);
}

</style>