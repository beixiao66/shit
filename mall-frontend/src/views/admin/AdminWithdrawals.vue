<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWithdrawals, processWithdrawal, type Withdrawal } from '@/api/merchant'

const list = ref<Withdrawal[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref<number | undefined>(undefined)

async function load() {
  const res = await getWithdrawals(status.value, page.value, pageSize.value)
  list.value = res.records
  total.value = res.total
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  load()
}

async function process(row: Withdrawal, pass: boolean) {
  let reason: string | undefined
  if (!pass) {
    const { value } = await ElMessageBox.prompt('请输入驳回原因（余额将回补）', '驳回', { inputValue: '' })
    reason = value
  }
  await processWithdrawal(row.id, pass, reason)
  ElMessage.success(pass ? '已标记打款成功' : '已驳回，余额已回补')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">提现处理</h1>
      <el-select v-model="status" style="width: 140px" @change="page = 1; load()">
        <el-option :value="undefined" label="全部" />
        <el-option :value="0" label="待处理" />
        <el-option :value="1" label="已成功" />
        <el-option :value="2" label="已驳回" />
      </el-select>
    </div>

    <el-table :data="list" border>
      <el-table-column label="提现单号" width="200">
        <template #default="{ row }"><span class="md-num">{{ row.withdrawalNo }}</span></template>
      </el-table-column>
      <el-table-column prop="merchantId" label="商家ID" width="90" />
      <el-table-column label="金额" width="110">
        <template #default="{ row }"><span class="md-num">{{ row.amount.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column prop="bankName" label="开户行" min-width="120" />
      <el-table-column prop="accountNo" label="卡号" min-width="150" />
      <el-table-column prop="holder" label="持卡人" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'warning' : row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 0 ? '待处理' : row.status === 1 ? '已成功' : '已驳回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="success" @click="process(row, true)">打款成功</el-button>
            <el-button link type="danger" @click="process(row, false)">驳回</el-button>
          </template>
          <span v-else-if="row.status === 2" class="muted">{{ row.rejectReason }}</span>
          <span v-else class="muted">已完成</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      class="pager"
      layout="sizes, prev, pager, next, total"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      :total="total"
      @current-change="load"
      @size-change="onSizeChange"
    />
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  margin: 0;
  font-family: var(--md-font-display);
  font-size: 22px;
  font-weight: 700;
}
.muted {
  color: var(--md-color-ink-sub);
  font-size: 13px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
