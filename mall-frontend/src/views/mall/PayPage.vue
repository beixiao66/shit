<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPay, getPayStatus, mockPayCallback } from '@/api/pay'
import { getMyOrders } from '@/api/order'

const route = useRoute()
const router = useRouter()
const orderNo = ref(String(route.params.orderNo ?? ''))
const payNo = ref('')
const paying = ref(false)
const paid = ref(false)
const failed = ref(false)
let timer: ReturnType<typeof setInterval> | undefined

async function load() {
  paying.value = true
  try {
    const no = await createPay(orderNo.value)
    payNo.value = String(no ?? '')
    paying.value = false
  } catch {
    ElMessage.error('创建支付单失败')
    paying.value = false
  }
}

/** 演示入口：模拟渠道回调（成功/失败），随后轮询支付状态 */
async function mockCallback(success: boolean) {
  await mockPayCallback(payNo.value, 'MOCK-TRADE-' + Date.now(), success ? await getAmount() : 1)
  paid.value = success
  failed.value = !success
  if (success) {
    await nextStep()
  }
  paying.value = false
}

/** 真实应支付金额（订单快照），金额核对非空即必须与支付单一致 */
async function getAmount() {
  try {
    const res = await getMyOrders({ page: 1, size: 50 })
    const o = res.records.find((r) => r.orderNo === orderNo.value)
    if (o) return Number(o.payAmount)
  } catch {
    /* 查询失败走演示金额 1（将被后端拒收，符合预期） */
  }
  return 1
}

async function nextStep() {
  timer = setInterval(async () => {
    const ok = await getPayStatus(orderNo.value)
    if (ok) {
      clearInterval(timer)
      ElMessage.success('支付成功，订单已进入商家发货流程')
      router.replace('/orders')
    }
  }, 1000)
  setTimeout(() => clearInterval(timer), 15000)
}

onMounted(load)
</script>

<template>
  <div class="pay">
    <section class="panel">
      <p class="eyebrow md-num">MALL-X / 支付</p>
      <h1 class="title">支付中</h1>
      <p v-if="paying" class="loading">创建支付单……</p>
      <template v-else>
        <p class="md-num payno">支付单号 {{ payNo }}</p>
        <p class="md-num orderno">订单 {{ orderNo }}</p>
        <p class="hint">当前为演示渠道（Mock）：点击下方按钮模拟支付宝沙箱回调（真实沙箱接入见项目文档的环境参数说明）。</p>
        <div class="actions">
          <el-button type="primary" size="large" :disabled="paid" @click="mockCallback(true)">模拟支付成功</el-button>
          <el-button size="large" :disabled="failed" @click="mockCallback(false)">模拟支付失败</el-button>
          <router-link class="back" to="/orders">返回订单列表</router-link>
        </div>
        <el-alert v-if="paid" type="success" title="支付成功，已同步订单状态与商家入账" :closable="false" />
        <el-alert v-if="failed" type="error" title="支付失败（回调金额不一致被拒）" :closable="false" />
      </template>
    </section>
  </div>
</template>

<style scoped>
.pay {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--md-color-bg-tint);
}
.panel {
  width: 100%;
  max-width: 480px;
  background: #fff;
  border: 1px solid var(--md-color-line);
  border-radius: var(--md-radius);
  padding: 40px;
}
.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: var(--md-color-ink-sub);
}
.title {
  margin: 0 0 18px;
  font-family: var(--md-font-display);
  font-size: 26px;
  font-weight: 700;
}
.loading {
  color: var(--md-color-ink-sub);
}
.payno {
  font-weight: 600;
  color: var(--md-color-primary);
}
.orderno {
  color: var(--md-color-ink-sub);
  font-size: 13px;
  margin: 4px 0 12px;
}
.hint {
  font-size: 13px;
  color: var(--md-color-ink-sub);
  line-height: 1.7;
}
.actions {
  display: flex;
  gap: 12px;
  margin: 18px 0;
}
.back {
  display: inline-block;
  font-size: 13px;
  color: var(--md-color-ink-sub);
  align-self: center;
}
</style>
