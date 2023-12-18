<script lang="ts" setup>
import {Setting} from "@element-plus/icons-vue";

defineProps({
  msg: {
    type: String,
    required: true
  }
})

import {ref} from 'vue'

const url = ref('');
const selectedDB = ref('');
const selectedIsolationLevel = ref('');
const history = ref(10);
const session = ref(20);
const txnPerSession = ref(100);
const opPerTxn = ref(5);
const key = ref(1000);
const selectedChecker = ref('');
const selectedCheckerIsolationLevel = ref('');
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
const useProfiler = ref(false);
const onSubmit = () => {
  console.log('submit!')
};
const handleNumChange = (value) => {
  console.log(value)
};
</script>

<template>
  <el-container class="layout-container-demo">
    <el-header style="text-align: center; font-size: 20px">Dashboard</el-header>
    <el-main>
      <el-row>
        <el-col :span="4">
        </el-col>
        <el-col :span="12">
          <el-form label-position="left" label-width="220px">
            <el-form-item label="URL">
              <el-input v-model="url" placeholder="JDBC URL" clearable class="fixed-width"></el-input>
            </el-form-item>
            <el-form-item label="DB Type">
              <el-select v-model="selectedDB" placeholder="" @change="handleSelectionChange" class="fixed-width">
                <el-option
                    v-for="option in dbOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="DB Isolation Level">
              <el-select v-model="selectedIsolationLevel" placeholder="" @change="handleSelectionChange"
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
                  v-model="history"
                  :min="10"
                  :max="100"
                  controls-position="right"
                  @change="handleNumChange"
              />
            </el-form-item>
            <el-form-item label="#Session">
              <el-input-number
                  v-model="session"
                  :min="20"
                  :max="100"
                  controls-position="right"
                  @change=""
              />
            </el-form-item>
            <el-form-item label="#Txn/Sess">
              <el-input-number
                  v-model="txnPerSession"
                  :min="100"
                  :max="200"
                  controls-position="right"
                  @change=""
              />
            </el-form-item>
            <el-form-item label="#Op/Txn">
              <el-input-number
                  v-model="opPerTxn"
                  :min="5"
                  :max="10"
                  controls-position="right"
                  @change=""
              />
            </el-form-item>
            <el-form-item label="#Key">
              <el-input-number
                  v-model="key"
                  :min="1000"
                  :max="2000"
                  controls-position="right"
                  @change=""
              />
            </el-form-item>
            <el-form-item label="Checker Type">
              <el-select v-model="selectedChecker" placeholder="" @change="handleSelectionChange" class="fixed-width">
                <el-option
                    v-for="option in checkerOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="DB Isolation Level">
              <el-select v-model="selectedCheckerIsolationLevel" placeholder="" @change="handleSelectionChange"
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
                  v-model="useProfiler"
                  class="ml-2"
                  style="--el-switch-on-color: #13ce66"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onSubmit">start</el-button>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-container-demo .el-header {
  position: relative;
  background-color: var(--el-color-primary-light-7);
  color: var(--el-text-color-primary);
  text-align: center;
  line-height: 60px;
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

.fixed-width {
  width: 600px;
}

</style>