<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditMerchant, getMerchants, type MerchantRow } from '@/api/merchant'

const list = ref<MerchantRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const applyStatus = ref<number | undefined>(0)

async function load() {
  const res = await getMerchants(applyStatus.value, page.value, pageSize.value)
  list.value = res.records
  total.value = res.total
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  load()
}

async function audit(row: MerchantRow, pass: boolean) {
  let reason: string | undefined
  if (!pass) {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { inputValue: '' })
    reason = value
  }
  await auditMerchant(row.id, pass, reason)
  ElMessage.success(pass ? `已通过：${row.merchantName}` : '已驳回')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">商家入驻审核</h1>
      <el-select v-model="applyStatus" style="width: 160px" @change="page = 1; load()">
        <el-option :value="0" label="待审核" />
        <el-option :value="1" label="已通过" />
        <el-option :value="2" label="已驳回" />
        <el-option :value="undefined" label="全部" />
      </el-select>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="merchantName" label="商家名" />
      <el-table-column prop="username" label="登录账号" width="140" />
      <el-table-column prop="phone" label="联系电话" width="140" />
      <el-table-column prop="shopName" label="店铺名" min-width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.applyStatus === 0 ? 'warning' : row.applyStatus === 1 ? 'success' : 'danger'">
            {{ row.applyStatus === 0 ? '待审核' : row.applyStatus === 1 ? '已通过' : '已驳回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="驳回原因" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.applyStatus === 0">
            <el-button link type="success" @click="audit(row, true)">通过</el-button>
            <el-button link type="danger" @click="audit(row, false)">驳回</el-button>
          </template>
          <span v-else class="muted">已处理</span>
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
