<script lang="ts" setup>
import {Setting, View} from "@element-plus/icons-vue";

defineProps({
  msg: {
    type: String,
    required: true
  }
})

import {reactive} from "vue";

const testingOption = reactive({
  db_url: '',
  db_type: '',
  db_isolation: '',
  workload_history: 10,
  workload_session: 20,
  workload_transaction: 100,
  workload_operation: 5,
  workload_key: 1000,
  checker_type: '',
  checker_isolation: '',
  profiler_enable: false
});

const dbOptions = [
  {label: 'MySQL', value: 'MYSQL'},
  {label: 'PostgreSQL', value: 'POSTGRES'},
  {label: 'H2', value: 'H2'}
];
const isolationLevelOptions = [
  {label: 'Read_Uncommitted', value: 'READ_UNCOMMITTED'},
  {label: 'Read_Committed', value: 'READ_COMMITTED'},
  {label: 'Repeatable_Read', value: 'REPEATABLE_READ'},
  {label: 'Serializable', value: 'SERIALIZABLE'},
  {label: 'Transaction_Snapshot', value: 'TRANSACTION_SNAPSHOT'},
];
const checkerOptions = [
  {label: 'PolySI', value: 'PolySI'},
  {label: 'C4', value: 'C4'}
];
const handleSelectionChange = (value: string) => {
  console.log('Selected:', value);
};

async function handleSubmit() {
  console.log(testingOption)
  fetch('http://localhost:8000/run', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(testingOption)
  }).then(response => {
    if (response.ok) {
      return response.json()
    }
    throw new Error('Network response was not ok.')
  }).then(data => {
    console.log(data)
  }).catch(err => {
    console.log('Error:',err)
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
                    v-for="option in isolationLevelOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="#History">
              <el-input-number
                  v-model="testingOption.workload_history"
                  :min="10"
                  :max="100"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Session">
              <el-input-number
                  v-model="testingOption.workload_session"
                  :min="20"
                  :max="100"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Txn/Sess">
              <el-input-number
                  v-model="testingOption.workload_transaction"
                  :min="100"
                  :max="200"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Op/Txn">
              <el-input-number
                  v-model="testingOption.workload_operation"
                  :min="5"
                  :max="10"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Key">
              <el-input-number
                  v-model="testingOption.workload_key"
                  :min="1000"
                  :max="2000"
                  controls-position="right"
              />
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
            <el-form-item label="DB Isolation Level">
              <el-select v-model="testingOption.checker_isolation" placeholder=""
                         @change="handleSelectionChange"
                         class="fixed-width">
                <el-option
                    v-for="option in isolationLevelOptions"
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
                  style="--el-switch-on-color: #13ce66"
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

</style>