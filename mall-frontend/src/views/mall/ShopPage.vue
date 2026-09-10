<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductList, getShopInfo, type Product, type ShopInfo } from '@/api/product'

const route = useRoute()
const router = useRouter()

const merchantId = route.params.merchantId as string
const shop = ref<ShopInfo | null>(null)
const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(12)
const loading = ref(false)

async function loadShop() {
  try {
    shop.value = await getShopInfo(merchantId)
  } catch {
    ElMessage.warning('店铺不存在或已关闭')
    router.replace('/')
  }
}

async function loadProducts() {
  loading.value = true
  try {
    const res = await getProductList({ page: page.value, size: pageSize.value, merchantId })
    products.value = res.records
    total.value = Number(res.total)
  } catch {
    ElMessage.warning('商品加载失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  loadProducts()
  document.querySelector('.grid')?.scrollIntoView({ behavior: 'smooth' })
}

function onSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadProducts()
}

onMounted(() => {
  loadShop()
  loadProducts()
})
</script>

<template>
  <div class="shop-page">
    <header class="head">
      <router-link class="brand" to="/">
        <span class="brand-logo">MX</span>
        <span class="brand-sub">商城</span>
      </router-link>
      <nav class="nav-right">
        <router-link class="nav-link" to="/">首页</router-link>
        <router-link class="nav-link" to="/cart">购物车</router-link>
      </nav>
    </header>

    <section v-if="shop" class="shop-hero">
      <div class="shop-logo">
        <img v-if="shop.shopLogo" :src="shop.shopLogo" :alt="shop.shopName" />
        <span v-else class="logo-fallback">MX</span>
      </div>
      <div class="shop-meta">
        <h1 class="shop-name">{{ shop.shopName }}</h1>
        <p v-if="shop.shopDesc" class="shop-desc">{{ shop.shopDesc }}</p>
        <p v-if="shop.shopAddress" class="shop-addr">📍 {{ shop.shopAddress }}</p>
      </div>
      <router-link class="back-home" to="/">← 返回首页</router-link>
    </section>

    <h2 class="section-title">店铺商品</h2>

    <section v-loading="loading" class="grid">
      <router-link v-for="p in products" :key="String(p.id)" class="card" :to="`/product/${p.id}`">
        <div class="card-img"><img :src="p.mainImg" :alt="p.title" /></div>
        <div class="card-body">
          <p class="card-title">{{ p.title }}</p>
          <div class="card-foot">
            <span class="price">¥ <b class="md-num">{{ p.minPrice?.toFixed(2) }}</b></span>
            <span class="sale md-num">已售 {{ p.saleCount ?? 0 }}</span>
          </div>
        </div>
      </router-link>
      <p v-if="!loading && !products.length" class="empty">该店铺暂无商品</p>
    </section>

    <div v-if="total" class="pager">
      <el-pagination
        background
        layout="prev, pager, next, sizes, total"
        :total="total"
        :page-size="pageSize"
        :page-sizes="[12, 24, 36]"
        :current-page="page"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.shop-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 24px 64px;
}
.head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 16px;
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
.nav-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}
.nav-link {
  font-size: 14px;
  color: var(--mx-ink-2);
}
.nav-link:hover {
  color: var(--mx-red);
}

/* 店铺头部 */
.shop-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  margin: 18px 0 8px;
  padding: 18px 20px;
  border: 1px solid var(--mx-line);
  border-radius: 12px;
  background: #fff;
}
.shop-logo {
  width: 72px;
  height: 72px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--mx-bg-2);
}
.shop-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.logo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: var(--mx-red);
  color: #fff;
  font-weight: 800;
  font-size: 22px;
}
.shop-meta {
  min-width: 0;
}
.shop-name {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: var(--mx-ink);
}
.shop-desc {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--mx-ink-2);
}
.shop-addr {
  margin: 0;
  font-size: 12px;
  color: var(--mx-ink-2);
}
.back-home {
  margin-left: auto;
  font-size: 13px;
  color: var(--mx-ink-2);
  white-space: nowrap;
}
.back-home:hover {
  color: var(--mx-red);
}

.section-title {
  margin: 20px 0 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--mx-ink);
}

/* 商品网格 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  gap: 16px;
  min-height: 120px;
}
.card {
  border: 1px solid var(--mx-line);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  transition: all 0.15s ease;
}
.card:hover {
  border-color: var(--mx-red);
  transform: translateY(-2px);
}
.card-img {
  aspect-ratio: 1;
  background: var(--mx-bg-2);
}
.card-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.card-body {
  padding: 10px 12px 12px;
}
.card-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--mx-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
.card-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.price {
  color: var(--mx-red);
  font-size: 13px;
}
.price b {
  font-size: 18px;
}
.sale {
  font-size: 12px;
  color: var(--mx-ink-2);
}
.empty {
  grid-column: 1 / -1;
  text-align: center;
  color: var(--mx-ink-2);
  padding: 60px 0;
}
.pager {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
