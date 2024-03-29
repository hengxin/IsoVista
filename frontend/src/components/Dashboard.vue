<script lang="ts" setup>
import {reactive, ref} from "vue"
import type {UploadProps, UploadUserFile} from "element-plus"
import {ElMessage, ElMessageBox} from "element-plus"
import {get_current_run_id, run} from "@/api/api"
import {InfoFilled} from "@element-plus/icons-vue"
import {useRouter} from "vue-router";

const backendUrl = ref(import.meta.env.VITE_BACKEND_URL);

const router = useRouter();

const testingOption = reactive({
  db_url: 'jdbc:mysql://127.0.0.1:3306',
  db_type: 'MYSQL',
  db_isolation: 'TRANSACTION_SERIALIZATION',
  db_username: 'root',
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
  workload_skipgeneration: false,
  history_path: 'user_history.txt',
  history_type: 'text',
  checker_isolation: ['SNAPSHOT_ISOLATION'],
  profiler_enable: true
});

const dbOptions = [
  {label: 'MySQL', value: 'MYSQL'},
  {label: 'PostgreSQL', value: 'POSTGRES'},
  {label: 'MariaDB', value: 'MARIA'}
];
const dbIsolationLevelOptions = [
  {label: 'Read Uncommitted', value: 'TRANSACTION_READ_UNCOMMITTED'},
  {label: 'Read Committed', value: 'TRANSACTION_READ_COMMITTED'},
  {label: 'Repeatable Read', value: 'TRANSACTION_REPEATABLE_READ'},
  {label: 'Serializable', value: 'TRANSACTION_SERIALIZATION'},
];
const distributionOptions = [
  {label: 'Uniform', value: 'uniform'},
  {label: 'Zipf', value: 'zipf'},
  {label: 'Hotspot', value: 'hotspot'},
];
const checkerIsolationLevelOptions = [
  {label: 'Read Committed', value: 'READ_COMMITTED'},
  {label: 'Repeatable Read', value: 'REPEATABLE_READ'},
  {label: 'Read Atomicity', value: 'READ_ATOMICITY'},
  {label: 'Transactional Causal Consistency', value: 'TRANSACTIONAL_CAUSAL_CONSISTENCY'},
  {label: 'Snapshot Isolation', value: 'SNAPSHOT_ISOLATION'},
  {label: 'Serializable', value: 'SERIALIZABLE'},
  {label: 'Viper SI', value: 'VIPER_SNAPSHOT_ISOLATION'},
  {label: 'PolySI+', value: 'CUSTOM_SNAPSHOT_ISOLATION'}
];
const historyTypeOptions = [
  {label: 'Text', value: 'text'},
  {label: 'Elle', value: 'elle'},
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
    get_current_run_id().then(res => {
      console.log(res.data)
      router.push({path: `/run_view/${res.data}`})
    })
  })
}

const fileList = ref<UploadUserFile[]>([])
const handleFileChange = () => {
  console.log(testingOption.workload_skipgeneration);
}
const handleRemove: UploadProps['onRemove'] = () => {
  testingOption.workload_skipgeneration = false
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
                <el-form-item label="JDBC URL">
                  <el-input v-model="testingOption.db_url" placeholder="JDBC URL" clearable
                            class="fixed-width"></el-input>
                  &nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content>
                      You can try jdbc:mysql://localhost:3306 for MYSQL, jdbc:postgresql://localhost15432 for
                      PostgreSQL and jdbc:mariadb://localhost:3307 for MariaDB.
                      This URL SHOULD be consistent with the DB Type option.
                    </template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
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
                    <template #content> The database type you want to test. This option SHOULD be consistent with the
                      JDBC URL.
                    </template>
                    <el-icon color="#409EFF">
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
                  </el-select>&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> The isolation level of transactions running according to the SQL-92 standard.
                    </template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
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
                <el-switch v-model="testingOption.workload_skipgeneration"
                           inactive-text="Skip Generation"/>
                <el-form-item label="#History" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_history"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> Number of histories in the workload.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Session" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_session"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> Number of simulated sessions.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Txn/Sess" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_transaction"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content>Number of transactions in each session.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Op/Txn" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_operation"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> Number of operations in each transaction.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Key" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_key"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> Number of the keys in the workload.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="Read Proportion" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_readproportion"
                      class="fixed-width"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> The proportion of read operations in the workload.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="Distribution" v-if="!testingOption.workload_skipgeneration">
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
                    <template #content> The distribution type of transaction access keys.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="History Type" v-if="testingOption.workload_skipgeneration">
                  <el-select v-model="testingOption.history_type" placeholder=""
                             @change="handleSelectionChange"
                             class="fixed-width"
                  >
                    <el-option
                        v-for="option in historyTypeOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value">
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-upload
                    v-model:file-list="fileList"
                    class="upload-demo"
                    :action="backendUrl + 'upload'"
                    multiple
                    :on-preview="handlePreview"
                    :on-remove="handleRemove"
                    :before-remove="beforeRemove"
                    :on-exceed="handleExceed"
                    @change="handleFileChange"
                    v-if="testingOption.workload_skipgeneration"
                >
                  <el-button type="primary">Upload</el-button>
                  <template #tip>
                    <div class="el-upload__tip">
                      upload your history file here
                    </div>
                  </template>
                </el-upload>
              </el-form>
            </el-carousel-item>
            <el-carousel-item class="el-carousel-item-demo">
              <el-form label-position="left"
                       label-width="35%"
              >
                <header class="form-header">
                  <h2>Checker Settings</h2>
                </header>
                <el-form-item label="Isolation Level">
                  <el-select v-model="testingOption.checker_isolation" placeholder=""
                             multiple
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
                <el-form-item v-show="false" label="Enable Profiler">
                  <el-switch
                      v-model="testingOption.profiler_enable"
                      class="ml-2"
                      @change="handleSwitch"
                  />
                </el-form-item>
                <el-form-item>
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
      <el-button size="large" type="primary" @click="handleSubmit()">Start</el-button>
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