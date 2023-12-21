<script lang="ts" setup>

defineProps({
  msg: {
    type: String,
    required: false
  }
})

import {reactive} from "vue";
import {ElLoading, ElMessage} from "element-plus";
import {run} from "@/api/api";

const testingOption = reactive({
  db_url: 'jdbc:mysql://localhost:3306',
  db_type: '',
  db_isolation: '',
  db_username: 'dbtest',
  db_password: 'dbtest_pwd',
  workload_type: 'general',
  workload_history: 10,
  workload_session: 20,
  workload_transaction: 100,
  workload_operation: 5,
  workload_key: 1000,
  workload_readproportion: 0.5,
  workload_distribution: '',
  checker_type: '',
  checker_isolation: '',
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

</script>

<template>
  <el-container class="layout-container-demo">
    <el-header>
    </el-header>

    <el-main>
      <el-row>
        <el-col :span="4">
        </el-col>
        <el-col :span="12">
          <el-form label-position="left" label-width="220px">
            <el-form-item label="URL">
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
              <el-input v-model="testingOption.db_username" placeholder="DB username" clearable class="fixed-width"></el-input>
            </el-form-item>
            <el-form-item label="Password">
              <el-input v-model="testingOption.db_password" placeholder="DB password" clearable class="fixed-width"></el-input>
            </el-form-item>
            <el-form-item label="#History">
              <el-input-number
                  v-model="testingOption.workload_history"
                  :min="1"
                  :max="100"
                  controls-position="right"
                  class="fixed-width"
              />
            </el-form-item>
            <el-form-item label="#Session">
              <el-input-number
                  v-model="testingOption.workload_session"
                  :min="1"
                  :max="1000"
                  controls-position="right"
                  class="fixed-width"
              />
            </el-form-item>
            <el-form-item label="#Txn/Sess">
              <el-input-number
                  v-model="testingOption.workload_transaction"
                  :min="1"
                  :max="1000"
                  controls-position="right"
                  class="fixed-width"
              />
            </el-form-item>
            <el-form-item label="#Op/Txn">
              <el-input-number
                  v-model="testingOption.workload_operation"
                  :min="1"
                  :max="1000"
                  controls-position="right"
                  class="fixed-width"
              />
            </el-form-item>
            <el-form-item label="#Key">
              <el-input-number
                  v-model="testingOption.workload_key"
                  :min="1"
                  :max="100000000"
                  controls-position="right"
                  class="fixed-width"
              />
            </el-form-item>
            <el-form-item label="Read Proportion">
              <el-input-number
                  v-model="testingOption.workload_readproportion"
                  :min="0"
                  :max="1"
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
          </el-form>
        </el-col>
      </el-row>
    </el-main>
  </el-container>
</template>

<style scoped>

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

.fixed-width {
  width: 600px;
}

.el-input .el-input__inner {text-align:left}

</style>