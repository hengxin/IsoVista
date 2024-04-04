<script lang="ts" setup>
import {reactive, ref, onMounted} from "vue"
import type {UploadProps, UploadUserFile} from "element-plus"
import {ElMessage, ElMessageBox} from "element-plus"
import {get_current_run_id, run} from "@/api/api"
import {InfoFilled, ArrowDown} from "@element-plus/icons-vue"
import {useRouter} from "vue-router";


const backendUrl = ref(import.meta.env.VITE_BACKEND_URL);

const router = useRouter();

const defaultOption = {
  db_url: 'jdbc:mysql://172.17.0.1:3306/',
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
}

const testingOption = reactive({
  db_url: 'jdbc:mysql://172.17.0.1:3306/',
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

const savedOptions = reactive([]);

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
  {label: 'Snapshot Isolation(Viper)', value: 'VIPER_SNAPSHOT_ISOLATION'},
  {label: 'Snapshot Isolation(PolySI+)', value: 'POLYSI+_SNAPSHOT_ISOLATION'},
  {label: 'Transactional Snapshot Isolation(ELLE)', value: 'ELLE_TRANSACTIONAL_SNAPSHOT_ISOLATION'},
];
const historyTypeOptions = [
  {label: 'Read-Write Register(text)', value: 'text'},
  {label: 'List-Append(elle)', value: 'elle'},
];
const handleSelectionChange = (value: string) => {
  console.log('Selected:', value);
};

const handleSwitch = (value: boolean) => {
  console.log('Enabled: ', value);
};

const hasWrongInput = reactive({
  db_url: false,
  db_username: false,
  db_password: false,
  workload_history: false,
  workload_session: false,
  workload_transaction: false,
  workload_operation: false,
  workload_key: false,
  workload_readproportion: false,
  history_type: false,
})

async function handleSubmit() {
  // check input validation
  const hasTrue = Object.values(hasWrongInput).some(value => value === true);
  if (hasTrue) {
    return ElMessage({
      message: 'Please check the required fields',
      type: 'warning'
    })
  }
  let listCount = 0;
  Object.values(testingOption).forEach(value => {
    if (typeof value === 'string' && value.startsWith('[') && value.endsWith(']')) {
      try {
        const parsed = JSON.parse(value);
        if (Array.isArray(parsed)) {
          listCount++;
        }
      } catch (e) {
      }
    }
  });
  if (listCount > 1) {
    return ElMessage({
      message: 'At most one list is allowed',
      type: 'warning'
    })
  }

  for (const key in testingOption) {
    if (!testingOption.hasOwnProperty(key) || !key.startsWith("workload_")) {
      continue;
    }
    if (typeof testingOption[key] === "string" && testingOption[key].startsWith("[")) {
      testingOption.workload_variable = key.replace('workload_', '')
    }
  }
  console.log(testingOption)
  // save current config
  let optionsArray = JSON.parse(localStorage.getItem('savedOptions') || '[]');
  const isUnique = !optionsArray.some(option => JSON.stringify(option) === JSON.stringify(testingOption));
  if (isUnique) {
    optionsArray.push(testingOption);
    localStorage.setItem('savedOptions', JSON.stringify(optionsArray));
    loadSavedOptions();
  }

  run(testingOption).then(res => {
    console.log(res)
    ElMessage({
      message: 'Add to run queue successfully',
      type: 'success',
    })
    router.push('/runs')
  })
}

function initializeDefaultOption() {
  if (!localStorage.getItem('Default')) {
    localStorage.setItem('Default', JSON.stringify(defaultOption));
  }
}
function loadSavedOptions() {
  const options = JSON.parse(localStorage.getItem('savedOptions') || '[]');
  savedOptions.splice(0, savedOptions.length, ...options);
}

const loadSetting = (command) => {
  if (command === 'default') {
    for (const key in testingOption) {
      delete testingOption[key];
    }
    Object.keys(defaultOption).forEach(key => {
      testingOption[key] = defaultOption[key];
    });
    return;
  }
  const selectedSetting = savedOptions[command];
  for (const key in testingOption) {
    delete testingOption[key];
  }
  Object.keys(selectedSetting).forEach(key => {
    testingOption[key] = selectedSetting[key];
  });
}

function clearStorage() {
  localStorage.removeItem('savedOptions');
  savedOptions.splice(0, savedOptions.length);
}

onMounted(() => {
  initializeDefaultOption();
  loadSavedOptions();
})

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
      "Upload at most ONE file."
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

// input check
const listRegex = /^\[\s*\d+(?:\s*,\s*\d+)*\s*\]$/
const readProportionListRegex = /^\[\s*(-?\d+(\.\d+)?)(\s*,\s*-?\d+(\.\d+)?)*\s*\]$/
const numberRegex = /^-?\d+$/
const readProportionNumberRegex = /^-?\d+(\.\d+)?$/
const lowerBound = ref(1)
const upperBound = ref(50)
const boxName = ref('')
async function checkInput(value, inputBox) {
  switch (inputBox) {
    case 'workload_session':
      lowerBound.value = 1
      upperBound.value = 100
      boxName.value = '#Session'
      break
    case 'workload_transaction':
      lowerBound.value = 1
      upperBound.value = 1000
      boxName.value = '#Txn/Session'
      break
    case 'workload_operation':
      lowerBound.value = 1
      upperBound.value = 50
      boxName.value = '#Operation/Txn'
      break
    case 'workload_key':
      lowerBound.value = 1
      upperBound.value = 100000000
      boxName.value = '#Key'
      break
    case 'workload_readproportion':
      lowerBound.value = 0
      upperBound.value = 1
      boxName.value = 'Read Proportion'
      break
  }
  if (value.trim().length === 0) {
    hasWrongInput[inputBox] = true
    return ElMessage({
      message: 'Please enter ' + boxName.value,
      type: 'warning'
    })
  }
  const number = inputBox === 'workload_readproportion' ? readProportionNumberRegex : numberRegex
  const list = inputBox === 'workload_readproportion' ? readProportionListRegex : listRegex
  if (!number.test(value.toString()) && !list.test(value.toString())) {
    hasWrongInput[inputBox] = true
    return ElMessage({
      message: 'Please enter a NUMBER or a LIST like [5,10,20,30] in ' + boxName.value,
      type: 'warning'
    })
  }
  if (number.test(value.toString())) {
    if (value < lowerBound.value || value > upperBound.value) {
      hasWrongInput[inputBox] = true
      return ElMessage({
        message: `Please enter a number between ${lowerBound.value} and ${upperBound.value} in ${boxName.value}`,
        type: 'warning'
      })
    }
  } else {
    // set list
    const list = JSON.parse(value)
    for (let i = 0; i < list.length; i++) {
      if (list[i] < lowerBound.value || list[i] > upperBound.value) {
        hasWrongInput[inputBox] = true
        return ElMessage({
          message: `Please make sure numbers in the LIST are between ${lowerBound.value} and ${upperBound.value} in ${boxName.value}`,
          type: 'warning'
        })
      }
    }
  }
  // pass all the tests
  hasWrongInput[inputBox] = false
}

async function handleHistoryChange(value) {
  if (value.trim().length === 0 || !numberRegex.test(value)) {
    hasWrongInput['workload_history'] = true
    return ElMessage({
      message: 'Please enter history number',
      type: 'warning'
    })
  }
  hasWrongInput['workload_history'] = false
}

async function handleurlChange(value) {
  if (value.trim().length === 0) {
    hasWrongInput['db_url'] = true
    return ElMessage({
      message: 'Please enter JDBC URL',
      type: 'warning'
    })
  }
  hasWrongInput['db_url'] = false
}

async function handleUserChange(value) {
  if (value.trim().length === 0) {
    hasWrongInput['db_username'] = true
    return ElMessage({
      message: 'Please enter DB username',
      type: 'warning'
    })
  }
  hasWrongInput['db_username'] = false
}

async function handleSessionChange(value) {
  await checkInput(value, 'workload_session')
}

async function handleTransactionChange(value) {
  await checkInput(value, 'workload_transaction')
}

async function handleOperationChange(value) {
  await checkInput(value, 'workload_operation')
}
async function handleKeyChange(value) {
  await checkInput(value, 'workload_key')
}

async function handleReadChange(value) {
  await checkInput(value, 'workload_readproportion')
}

</script>

<template>
  <el-container class="layout-container-demo">
    <el-header>
      <el-dropdown trigger="click" @command="loadSetting">
        <el-button type="primary">
          Presets<el-icon class="el-icon--right"><arrow-down /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :command="'default'">
              Default
            </el-dropdown-item>
            <el-dropdown-item v-for="(option, index) in savedOptions"
                              :key="index"
                              :command="index">
              {{ 'Config ' + (index + 1) }}</el-dropdown-item>

          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button @click="clearStorage" type="success">
        Clear
      </el-button>
    </el-header>

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
                  <h2>Database Setting</h2>
                </header>
                <el-form-item label="JDBC URL">
                  <el-input v-model="testingOption.db_url" placeholder="JDBC URL" clearable
                            class="fixed-width"
                            @blur="handleurlChange(testingOption.db_url)"
                  ></el-input>
                  &nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content>
                      You can try jdbc:mysql://172.17.0.1:3306/ for MYSQL, jdbc:postgresql://172.17.0.1:5432/ for
                      PostgreSQL and jdbc:mariadb://172.17.0.1:3307/ for MariaDB.
                      This URL SHOULD be consistent with the Database option.
                    </template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="Database">
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
                    <template #content> The database you want to test. This option SHOULD be consistent with the
                      JDBC URL.
                    </template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="Isolation Level">
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
                            class="fixed-width"
                            @blur="handleUserChange(testingOption.db_username)"
                  ></el-input>
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
                  <h2>Workload Setting</h2>
                </header>
                <el-switch v-model="testingOption.workload_skipgeneration"
                           inactive-text="Skip Generator"/>
                <el-form-item label="#History" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_history"
                      class="fixed-width"
                      @blur="handleHistoryChange(testingOption.workload_history)"
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
                      @blur="handleSessionChange(testingOption.workload_session)"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> Number of simulated sessions.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Txn/Session" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_transaction"
                      class="fixed-width"
                      @blur="handleTransactionChange(testingOption.workload_transaction)"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content>Number of transactions in each session.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="#Operation/Txn" v-if="!testingOption.workload_skipgeneration">
                  <el-input
                      v-model="testingOption.workload_operation"
                      class="fixed-width"
                      @blur="handleOperationChange(testingOption.workload_operation)"
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
                      @blur="handleKeyChange(testingOption.workload_key)"
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
                      @blur="handleReadChange(testingOption.workload_readproportion)"
                  />&nbsp;&nbsp;
                  <el-tooltip placement="top">
                    <template #content> The proportion of read operations in the workload.</template>
                    <el-icon color="#409EFF">
                      <InfoFilled/>
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
                <el-form-item label="Skewness" v-if="!testingOption.workload_skipgeneration">
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
                    :limit="1"
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
                  <h2>Checker Setting</h2>
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