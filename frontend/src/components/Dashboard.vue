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
  url: '',
  selectedDB: '',
  selectedIsolationLevel: '',
  history: 10,
  session: 20,
  txnPerSession: 100,
  opPerTxn: 5,
  key: 1000,
  selectedChecker: '',
  selectedCheckerIsolationLevel: '',
  useProfiler: false
});

const dbOptions = [
  {label: 'MySQL', value: 'mysql'},
  {label: 'PostgreSQL', value: 'postgres'},
  {label: 'MariaDB', value: 'mariadb'}
];
const isolationLevelOptions = [
  {label: 'Read_Uncommitted', value: 'read_uncommitted'},
  {label: 'Read_Committed', value: 'read_committed'},
  {label: 'Repeatable_Read', value: 'repeatable_read'},
  {label: 'Serializable', value: 'serializable'}
];
const checkerOptions = [
  {label: 'PolySI', value: 'polysi'},
  {label: 'C4', value: 'c4'}
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
              <el-input v-model="testingOption.url" placeholder="JDBC URL" clearable class="fixed-width"></el-input>
            </el-form-item>
            <el-form-item label="DB Type">
              <el-select v-model="testingOption.selectedDB" placeholder="" @change="handleSelectionChange"
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
              <el-select v-model="testingOption.selectedIsolationLevel" placeholder="" @change="handleSelectionChange"
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
                  v-model="testingOption.history"
                  :min="10"
                  :max="100"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Session">
              <el-input-number
                  v-model="testingOption.session"
                  :min="20"
                  :max="100"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Txn/Sess">
              <el-input-number
                  v-model="testingOption.txnPerSession"
                  :min="100"
                  :max="200"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Op/Txn">
              <el-input-number
                  v-model="testingOption.opPerTxn"
                  :min="5"
                  :max="10"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="#Key">
              <el-input-number
                  v-model="testingOption.key"
                  :min="1000"
                  :max="2000"
                  controls-position="right"
              />
            </el-form-item>
            <el-form-item label="Checker Type">
              <el-select v-model="testingOption.selectedChecker" placeholder="" @change="handleSelectionChange"
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
              <el-select v-model="testingOption.selectedCheckerIsolationLevel" placeholder=""
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
                  v-model="testingOption.useProfiler"
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