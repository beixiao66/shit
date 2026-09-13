<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  STATUS_TEXT,
  adminShipOrder,
  getAdminOrders,
  getMerchantOrders,
  shipOrder,
  type OrderVO,
} from '@/api/order'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
/** 商家(type=2) 管本店订单；管理员(type=1) 管全局订单并可代发货 */
const isMerchant = () => userStore.type === 2

const STATUS_OPTIONS = [
  { label: '全部', value: undefined as number | undefined },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已收货', value: 3 },
  { label: '已取消', value: 4 },
]

const list = ref<OrderVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref<number | undefined>(undefined)
/** 搜索关键字：订单号 / 收货人 / 商品名 */
const keyword = ref('')
const shipDialog = ref(false)
const shipOrderNo = ref('')
const ship = reactive({ logisticsCompany: '', trackingNo: '' })

async function load() {
  const query = {
    status: status.value,
    keyword: keyword.value.trim() || undefined,
    page: page.value,
    size: pageSize.value,
  }
  const res = isMerchant() ? await getMerchantOrders(query) : await getAdminOrders(query)
  list.value = res.records
  total.value = res.total
}

/** 搜索：回到第 1 页重查 */
function onSearch() {
  page.value = 1
  load()
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  load()
}

function openShip(order: OrderVO) {
  shipOrderNo.value = order.orderNo
  ship.logisticsCompany = ''
  ship.trackingNo = ''
  shipDialog.value = true
}

async function submitShip() {
  if (!ship.logisticsCompany || !ship.trackingNo) {
    ElMessage.warning('请填写物流公司与运单号')
    return
  }
  if (isMerchant()) {
    await shipOrder(shipOrderNo.value, ship.logisticsCompany, ship.trackingNo)
  } else {
    await adminShipOrder(shipOrderNo.value, ship.logisticsCompany, ship.trackingNo)
  }
  ElMessage.success('发货成功')
  shipDialog.value = false
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">{{ isMerchant() ? '店铺订单' : '订单管理' }}</h1>
      <div class="filters">
        <el-input
          v-model="keyword"
          class="search"
          placeholder="搜索订单号 / 收货人 / 商品名"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="status" style="width: 140px" @change="page = 1; load()">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.label" :label="s.label" :value="s.value ?? 'all'" />
        </el-select>
      </div>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="orderNo" label="订单号" width="200">
        <template #default="{ row }"><span class="md-num">{{ row.orderNo }}</span></template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="100" />
      <el-table-column label="金额" width="120">
        <template #default="{ row }"><span class="md-num">¥ {{ row.payAmount.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : row.status === 4 ? 'info' : 'primary'">
            {{ STATUS_TEXT[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="物流" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="md-num" v-if="row.trackingNo">{{ row.logisticsCompany }}·{{ row.trackingNo }}</span>
          <span v-else class="muted">未发货</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" link type="primary" @click="openShip(row)">发货</el-button>
          <span v-else class="muted">-</span>
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

    <el-dialog v-model="shipDialog" title="物流发货" width="420">
      <p class="md-num ship-no">{{ shipOrderNo }}</p>
      <el-form label-width="80px">
        <el-form-item label="物流公司">
          <el-input v-model="ship.logisticsCompany" placeholder="如：顺丰速运" />
        </el-form-item>
        <el-form-item label="运单号">
          <el-input v-model="ship.trackingNo" placeholder="如：SF123456" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
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
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
}
.search {
  width: 260px;
}
.muted {
  color: var(--md-color-ink-sub);
  font-size: 13px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.ship-no {
  font-weight: 600;
  color: var(--md-color-primary);
  margin-bottom: 12px;
}
</style>
