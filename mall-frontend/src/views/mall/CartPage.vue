<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCart, mergeCart, removeCart, updateCartChecked, updateCartCount, type CartItem } from '@/api/cart'
import { createOrder } from '@/api/order'
import { getAddresses, type Address } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 游客车（localStorage；登录后合并到 cart 表） */
interface GuestItem extends CartItem {
  localId: string
}

const items = ref<CartItem[] | GuestItem[]>([])
const loading = ref(false)
const checkoutDialog = ref(false)
const payDialog = ref(false)
const createdOrders = ref<string[]>([])
const receiver = reactive({ name: '', phone: '', address: '' })
/** 收货地址列表（结算时可下拉选择） */
const addrList = ref<Address[]>([])
/** 当前选中的地址 id；null 表示未选择（需手动填写） */
const selectedAddrId = ref<number | null>(null)

const isLogged = computed(() => !!userStore.token)
const checkedItems = computed(() => items.value.filter((i) => i.checked === 1))
const totalAmount = computed(() => checkedItems.value.reduce((s, i) => s + (i.price ?? 0) * i.count, 0))
const allChecked = computed(() => items.value.length > 0 && checkedItems.value.length === items.value.length)

async function onAllChecked(checked: number) {
  for (const i of items.value) {
    if (i.checked !== checked) {
      if (!isLogged.value) i.checked = checked
      else await updateCartChecked(i.skuId, checked)
    }
  }
  if (!isLogged.value) writeGuestCart(items.value as GuestItem[])
}

const GUEST_KEY = 'mall_guest_cart'

/** 规格 JSON → "颜色:白色;尺码:L" 可读文本 */
function formatSpec(specJson?: string): string {
  if (!specJson) return '单规格'
  try {
    const spec: Record<string, string> = JSON.parse(specJson)
    const parts = Object.entries(spec).map(([k, v]) => `${k}：${v}`)
    return parts.length ? parts.join('；') : '单规格'
  } catch {
    return '单规格'
  }
}

function readGuestCart(): GuestItem[] {
  try {
    return JSON.parse(localStorage.getItem(GUEST_KEY) ?? '[]')
  } catch {
    return []
  }
}

function writeGuestCart(list: GuestItem[]) {
  localStorage.setItem(GUEST_KEY, JSON.stringify(list))
}

/** 游客本地购物车接口占位（加购走 ProductDetail；合并见下方流程） */
async function load() {
  loading.value = true
  try {
    if (!isLogged.value) {
      items.value = readGuestCart()
      return
    }
    // 登录后：先合并本地车（同 SKU 由后端累加），清空本地
    const local = readGuestCart()
    if (local.length) {
      await mergeCart(local.map((l) => ({ skuId: l.skuId, count: l.count })))
      writeGuestCart([])
    }
    items.value = await getCart()
  } finally {
    loading.value = false
  }
}

function guestById(id: number, list: GuestItem[] = readGuestCart()) {
  return list.find((i) => i.skuId === id)
}

async function onCountChange(item: CartItem | GuestItem, count: number) {
  if (!isLogged.value) {
    const list = readGuestCart()
    const g = list.find((i) => i.skuId === item.skuId)
    if (g) g.count = count
    writeGuestCart(list)
  } else {
    await updateCartCount(item.skuId, count)
  }
  // 同步本行数量，触发小计/合计联动更新
  const local = items.value.find((i) => i.skuId === item.skuId)
  if (local) local.count = count
}

async function onChecked(item: CartItem | GuestItem, checked: number) {
  if (!isLogged.value) {
    const list = readGuestCart()
    const g = list.find((i) => i.skuId === item.skuId)
    if (g) g.checked = checked
    writeGuestCart(list)
    return
  }
  await updateCartChecked(item.skuId, checked)
}

async function remove(item: CartItem | GuestItem) {
  await ElMessageBox.confirm('从购物车删除该商品？', '提醒', { type: 'warning' })
  if (!isLogged.value) {
    writeGuestCart(readGuestCart().filter((i) => i.skuId !== item.skuId))
    await load()
    return
  }
  await removeCart(item.skuId)
  await load()
}

function addrText(a: Address): string {
  return [a.province, a.city, a.district, a.detail].filter(Boolean).join(' ')
}

function fillReceiver(a: Address) {
  receiver.name = a.receiver
  receiver.phone = a.phone
  receiver.address = addrText(a)
}

/** 选择收货地址：选中下拉项即回填；未选则手动填写 */
function selectAddr(id: number | null) {
  selectedAddrId.value = id
  if (id == null) {
    receiver.name = ''
    receiver.phone = ''
    receiver.address = ''
    return
  }
  const addr = addrList.value.find((a) => a.id === id)
  if (addr) fillReceiver(addr)
}

async function openCheckout() {
  if (!isLogged.value) {
    ElMessage.info('请先登录再结算（游客购物车将在登录后自动合并）')
    router.push('/login')
    return
  }
  if (!checkedItems.value.length) {
    ElMessage.warning('请先勾选商品')
    return
  }
  try {
    addrList.value = await getAddresses()
  } catch {
    addrList.value = []
  }
  // 默认选中默认地址；无地址则进入手动填写
  const def = addrList.value.find((a) => a.isDefault === 1) ?? addrList.value[0]
  selectedAddrId.value = def?.id ?? null
  if (def) {
    fillReceiver(def)
  } else {
    receiver.name = ''
    receiver.phone = ''
    receiver.address = ''
  }
  checkoutDialog.value = true
}

async function submitOrder() {
  if (!receiver.name || !receiver.phone || !receiver.address) {
    ElMessage.warning('请填写完整收货信息')
    return
  }
  try {
    createdOrders.value = await createOrder({
      reqId: crypto.randomUUID(),
      items: checkedItems.value.map((i) => ({ skuId: i.skuId, count: i.count })),
      receiverName: receiver.name,
      receiverPhone: receiver.phone,
      receiverAddress: receiver.address,
      fromCart: true,
    })
    checkoutDialog.value = false
    payDialog.value = true
    await load()
  } catch {
    /* 拦截器统一提示 */
  }
}

function goOrders() {
  payDialog.value = false
  router.push('/orders')
}

function goPay(orderNo: string) {
  payDialog.value = false
  router.push(`/pay/${orderNo}`)
}

onMounted(load)
</script>

<template>
  <div class="cart">
    <header class="head">
      <router-link class="brand" to="/"><span class="brand-logo">MX</span><span class="brand-sub">购物车</span></router-link>
      <router-link class="nav-link" to="/orders">我的订单</router-link>
    </header>

    <p v-if="!isLogged" class="guest-tip">
      游客购物车暂存本机，<router-link to="/login">登录</router-link>后自动合并到账户。
    </p>

    <div v-loading="loading" class="cart-list">
      <div class="cart-head">
        <el-checkbox :model-value="allChecked" @change="(v: string | number | boolean) => onAllChecked(v ? 1 : 0)" />
        <span class="head-name">商品</span>
        <span class="head-price">单价</span>
        <span class="head-count">数量</span>
        <span class="head-amount">小计</span>
        <span class="head-op">操作</span>
      </div>

      <div v-for="row in items" :key="row.skuId" class="cart-row">
        <el-checkbox :model-value="row.checked === 1" @change="(v: string | number | boolean) => onChecked(row, v ? 1 : 0)" />
        <div class="goods">
          <div class="thumb"><img v-if="row.mainImg" :src="row.mainImg" alt="" /></div>
          <div>
            <p class="title">{{ row.productTitle || '商品' }}</p>
            <p class="spec">{{ formatSpec(row.specJson) }}</p>
          </div>
        </div>
        <span class="md-num price">¥ {{ (row.price ?? 0).toFixed(2) }}</span>
        <div class="count-box">
          <button class="count-btn" @click="onCountChange(row, Math.max(1, (row.count ?? 1) - 1))">−</button>
          <input class="count-input md-num" :value="row.count" readonly />
          <button class="count-btn" @click="onCountChange(row, (row.count ?? 1) + 1)">＋</button>
        </div>
        <span class="md-num amount">¥ {{ ((row.price ?? 0) * (row.count ?? 0)).toFixed(2) }}</span>
        <button class="del" @click="remove(row)">删除</button>
      </div>
    </div>

    <div v-if="items.length" class="bar">
      <span class="md-num bar-count">已选 {{ checkedItems.length }} 项</span>
      <span class="bar-total">合计 <span class="md-num bar-num">¥ {{ totalAmount.toFixed(2) }}</span></span>
      <el-button type="primary" size="large" class="bar-checkout" @click="openCheckout">去结算({{ checkedItems.length }})</el-button>
    </div>
    <p v-else-if="!loading" class="empty">购物车空空如也 —— <router-link to="/">去货架挑点东西</router-link></p>

    <el-dialog v-model="checkoutDialog" title="填写收货信息（下单快照）" width="460">
      <div class="addr-picker">
        <p class="addr-title">选择收货地址</p>
        <el-select
          v-model="selectedAddrId"
          class="addr-select"
          placeholder="请选择收货地址"
          clearable
          @change="selectAddr"
        >
          <el-option
            v-for="a in addrList"
            :key="a.id"
            :label="`${a.receiver} · ${a.phone} · ${addrText(a)}${a.isDefault === 1 ? '（默认）' : ''}`"
            :value="a.id"
          />
        </el-select>
        <p v-if="!addrList.length" class="addr-empty">暂无收货地址，请手动填写下方信息</p>
      </div>

      <el-form label-width="80px">
        <el-form-item label="收货人">
          <el-input v-model="receiver.name" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="receiver.phone" />
        </el-form-item>
        <el-form-item label="收货地址">
          <el-input v-model="receiver.address" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutDialog = false">取消</el-button>
        <el-button type="primary" @click="submitOrder">提交订单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="payDialog" title="下单成功" width="440">
      <p class="pay-tip">已拆分为 {{ createdOrders.length }} 笔订单（跨店购物车逐店一单）：</p>
      <p v-for="no in createdOrders" :key="no" class="pay-row">
        <span class="md-num pay-no">{{ no }}</span>
        <el-button size="small" type="primary" @click="goPay(no)">去支付</el-button>
      </p>
      <template #footer>
        <el-button @click="goOrders">稍后支付，查看订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cart {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 24px 64px;
}
.head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mx-line);
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}
.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--mx-red);
  color: #fff;
  font-weight: 800;
  font-size: 17px;
}
.brand-sub {
  font-size: 15px;
  font-weight: 600;
  color: var(--mx-ink);
}
.nav-link {
  font-size: 14px;
  color: var(--mx-ink-2);
}
.nav-link:hover {
  color: var(--mx-red);
}
.guest-tip {
  margin: 14px 0;
  font-size: 13px;
  color: var(--mx-ink-2);
}
.guest-tip a {
  color: var(--mx-red);
}

/* 自定义购物车列表 */
.cart-list {
  margin-top: 14px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid var(--mx-line);
}
.cart-head,
.cart-row {
  display: grid;
  grid-template-columns: 40px 1fr 120px 140px 110px 70px;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
}
.cart-head {
  border-bottom: 1px solid var(--mx-line);
  font-size: 12px;
  color: var(--mx-ink-2);
}
.cart-row {
  border-bottom: 1px solid var(--mx-line);
}
.cart-row:last-child {
  border-bottom: none;
}
.cart-row:hover {
  background: var(--mx-bg-2);
}
.head-name {
  grid-column: 2;
}
.head-price,
.head-count,
.head-amount,
.head-op {
  text-align: center;
}
.goods {
  display: flex;
  align-items: center;
  gap: 12px;
}
.thumb {
  width: 64px;
  height: 64px;
  background: var(--mx-bg-2);
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.title {
  margin: 0;
  font-size: 14px;
  color: var(--mx-ink);
}
.spec {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--mx-ink-2);
}
.price {
  text-align: center;
  font-size: 13px;
  color: var(--mx-ink-2);
}
.count-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}
.count-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--mx-line);
  background: #fff;
  color: var(--mx-ink);
  font-size: 14px;
  cursor: pointer;
}
.count-btn:hover {
  border-color: var(--mx-red);
  color: var(--mx-red);
}
.count-input {
  width: 40px;
  text-align: center;
  border: 1px solid var(--mx-line);
  border-left: none;
  border-right: none;
  height: 28px;
  font-size: 13px;
  color: var(--mx-ink);
  outline: none;
}
.amount {
  text-align: center;
  font-weight: 700;
  color: var(--mx-red);
}
.del {
  border: none;
  background: transparent;
  color: var(--mx-ink-2);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.del:hover {
  color: var(--mx-red);
  background: var(--mx-red-soft);
}

/* 底部结算栏 */
.bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 20px;
  padding: 16px 22px;
  background: #fff;
  border: 1px solid var(--mx-line);
  border-radius: 12px;
  box-shadow: 0 -2px 12px rgba(29, 33, 41, 0.05);
}
.bar-count {
  font-size: 13px;
  color: var(--mx-ink-2);
}
.bar-total {
  margin-left: auto;
  font-size: 14px;
  color: var(--mx-ink-2);
}
.bar-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--mx-red);
}
.bar-checkout {
  border-radius: 22px;
  padding: 0 34px;
}
.empty {
  text-align: center;
  color: var(--mx-ink-2);
  padding: 70px 0;
}
.empty a {
  color: var(--mx-red);
}
/* 收货地址下拉 */
.addr-picker {
  margin-bottom: 14px;
}
.addr-title {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--mx-ink-2);
}
.addr-select {
  width: 100%;
}
.addr-empty {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--mx-ink-2);
}

.pay-tip {
  font-size: 14px;
}
.pay-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
}
.pay-no {
  font-size: 13px;
  color: var(--mx-red);
  font-weight: 600;
}

@media (max-width: 860px) {
  .cart-head {
    display: none;
  }
  .cart-row {
    grid-template-columns: 36px 1fr 70px;
    grid-template-rows: auto auto;
  }
  .goods {
    grid-column: 2;
  }
  .price,
  .count-box {
    display: none;
  }
  .amount {
    grid-column: 3;
  }
  .del {
    grid-column: 3;
    grid-row: 2;
  }
}
</style>
