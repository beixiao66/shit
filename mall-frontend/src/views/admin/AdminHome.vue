<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getDashboard, type Dashboard } from '@/api/report'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const $chart = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
const data = ref<Dashboard | null>(null)
/** 近 7 日是否有营收数据（无数据时图表下方给出口径提示） */
const hasTrend = ref(false)

async function load() {
  // 商家看本店口径（传自己的 merchantId），管理员看全平台
  const merchantId = userStore.type === 2 ? userStore.id : undefined
  data.value = await getDashboard(merchantId)
  // 等 v-if="data" 内的图表容器挂载后再初始化 ECharts；否则 $chart 为 null，图表不渲染
  await nextTick()
  renderChart()
}

/** 近 7 天日期（含今天，YYYY-MM-DD）：SQL 只返回有订单的日期，前端补零成连续 7 天 */
function last7Days(): string[] {
  const days: string[] = []
  const today = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(today.getDate() - i)
    days.push(
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
    )
  }
  return days
}

function renderChart() {
  if (!$chart.value || !data.value) return
  chart ??= echarts.init($chart.value)
  const trend = data.value.dailyTrend ?? []
  const amountByDay = new Map(trend.map((t) => [t.day, Number(t.amount)]))
  const days = last7Days()
  const amounts = days.map((d) => amountByDay.get(d) ?? 0)
  hasTrend.value = amounts.some((v) => v > 0)
  chart.setOption({
    grid: { left: 10, right: 10, top: 30, bottom: 10, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: days, axisLine: { lineStyle: { color: '#c9d2e0' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef1f6' } } },
    series: [
      {
        name: '营收',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: amounts,
        itemStyle: { color: '#e8b04b' },
        lineStyle: { color: '#e8b04b', width: 2.5 },
        areaStyle: { color: 'rgba(232, 176, 75, 0.12)' },
      },
    ],
  })
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <div class="dash">
    <p class="eyebrow md-num">MALL-X / DASHBOARD</p>
    <h1 class="title">{{ userStore.type === 2 ? '本店看板' : '平台数据看板' }}</h1>

    <template v-if="data">
      <div class="cards">
        <div class="card">
          <span class="label">营收（含已发货/已收货）</span>
          <span class="md-num value">¥ {{ (data.revenue ?? 0).toFixed(2) }}</span>
        </div>
        <div class="card">
          <span class="label">进账</span>
          <span class="md-num value">¥ {{ (data.incoming ?? 0).toFixed(2) }}</span>
        </div>
        <div class="card">
          <span class="label">出账（退款+提现）</span>
          <span class="md-num value">¥ {{ (data.outgoing ?? 0).toFixed(2) }}</span>
        </div>
        <div class="card">
          <span class="label">{{ userStore.type === 2 ? '本店' : '入驻商家' }}</span>
          <span class="md-num value">{{ data.merchantCount ?? 0 }}</span>
        </div>
      </div>

      <div class="panel chart-panel">
        <p class="panel-title">近 7 日营收趋势</p>
        <div ref="$chart" class="chart" />
        <p v-if="!hasTrend" class="chart-empty">
          近 7 日暂无营收数据 —— 营收按下单时间统计"已支付 / 已发货 / 已收货"订单，待支付与已退款订单不计入
        </p>
      </div>

      <div v-if="data.merchantStats" class="cards sub">
        <div class="card sm">
          <span class="label">入驻统计（通过/待审/驳回）</span>
          <span class="md-num value">
            {{ data.merchantStats.approved }} / {{ data.merchantStats.pending }} / {{ data.merchantStats.rejected }}
          </span>
        </div>
      </div>
    </template>
    <el-skeleton v-else :rows="5" animated />
  </div>
</template>

<style scoped>
.dash {
  padding: 8px 4px;
}
.eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  color: var(--md-color-ink-sub);
  margin: 0 0 6px;
}
.title {
  margin: 0 0 22px;
  font-family: var(--md-font-display);
  font-weight: 700;
  font-size: 26px;
  color: var(--md-color-ink);
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--md-color-line);
  border-radius: var(--md-radius);
}
.card.sm {
  grid-column: 1 / -1;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.label {
  font-size: 12px;
  color: var(--md-color-ink-sub);
}
.value {
  font-size: 24px;
  font-weight: 600;
  color: var(--md-color-primary);
}
.panel {
  background: #fff;
  border: 1px solid var(--md-color-line);
  border-radius: var(--md-radius);
  padding: 18px 20px;
}
.panel-title {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
}
.chart {
  height: 300px;
}
.chart-empty {
  margin: 10px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--md-color-ink-sub);
}
.cards.sub {
  margin-top: 16px;
}
</style>
