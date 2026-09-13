<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { applyWithdrawal, getMyWithdrawals, getShop, type Withdrawal } from '@/api/merchant'

const balance = ref('0.00')
const list = ref<Withdrawal[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const submiting = ref(false)
const form = reactive({ bankName: '', accountNo: '', holder: '', amount: 0 as number })

async function load() {
  const [shop, res] = await Promise.all([getShop(), getMyWithdrawals(page.value, pageSize.value)])
  balance.value = shop.balance.toFixed(2)
  list.value = res.records
  total.value = res.total
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  load()
}

async function submit() {
  if (!form.bankName || !form.accountNo || !form.holder || !form.amount || form.amount <= 0) {
    ElMessage.warning('请完整填写提现信息')
    return
  }
  submiting.value = true
  try {
    await applyWithdrawal({ ...form })
    ElMessage.success('提现申请已提交，等待平台打款')
    Object.assign(form, { bankName: '', accountNo: '', holder: '', amount: 0 })
    load()
  } finally {
    submiting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">提现</h1>
      <span class="md-num balance">当前余额 ¥ {{ balance }}</span>
    </div>

    <el-form label-width="90px" class="form">
      <el-form-item label="开户行">
        <el-input v-model="form.bankName" placeholder="如：招商银行深圳分行" />
      </el-form-item>
      <el-form-item label="银行卡号">
        <el-input v-model="form.accountNo" placeholder="卡号（仅快照存储）" />
      </el-form-item>
      <el-form-item label="持卡人">
        <el-input v-model="form.holder" />
      </el-form-item>
      <el-form-item label="提现金额">
        <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 200px" />
        <span class="md-num hint">余额 ¥ {{ balance }}</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submiting" @click="submit">提交提现申请</el-button>
      </el-form-item>
    </el-form>

    <h2 class="sub-title">提现记录</h2>
    <el-table :data="list" border>
      <el-table-column label="单号" width="210">
        <template #default="{ row }"><span class="md-num">{{ row.withdrawalNo }}</span></template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }"><span class="md-num">{{ row.amount.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column prop="bankName" label="开户行" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'warning' : row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 0 ? '待处理' : row.status === 1 ? '已成功' : '已驳回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="140">
        <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
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
  margin-bottom: 20px;
}
.title {
  margin: 0;
  font-family: var(--md-font-display);
  font-size: 22px;
  font-weight: 700;
}
.balance {
  font-size: 16px;
  font-weight: 600;
  color: var(--md-color-primary);
}
.form {
  max-width: 520px;
}
.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--md-color-ink-sub);
}
.sub-title {
  margin: 26px 0 12px;
  font-size: 16px;
  color: var(--md-color-ink);
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
