<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getRefunds, type RefundRecord } from '@/api/pay'

const list = ref<RefundRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref<number | undefined>(undefined)

async function load() {
  const res = await getRefunds(status.value, page.value, pageSize.value)
  list.value = res.records
  total.value = res.total
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">退款处理</h1>
      <el-select v-model="status" style="width: 140px" @change="page = 1; load()">
        <el-option :value="undefined" label="全部" />
        <el-option :value="0" label="处理中" />
        <el-option :value="1" label="已成功" />
        <el-option :value="2" label="已失败" />
      </el-select>
    </div>

    <el-table :data="list" border>
      <el-table-column label="退款单号" width="200">
        <template #default="{ row }"><span class="md-num">{{ row.refundNo }}</span></template>
      </el-table-column>
      <el-table-column label="订单号" width="200">
        <template #default="{ row }"><span class="md-num">{{ row.orderNo }}</span></template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }"><span class="md-num">{{ row.amount.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'danger'">
            {{ row.status === 1 ? '已成功' : row.status === 0 ? '处理中' : '已失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }"><span class="md-num">{{ (row.createTime ?? '').replace('T', ' ').slice(0, 19) }}</span></template>
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
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
