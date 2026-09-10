<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdverts, getCategories, type Advert, type Category } from '@/api/catalog'
import { getProductList, type Product } from '@/api/product'
import { getCart } from '@/api/cart'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const categories = ref<Category[]>([])
const adverts = ref<Advert[]>([])
const products = ref<Product[]>([])
const total = ref(0)
const loading = ref(false)
const activeCategory = ref<number>()
const keyword = ref('')
const cartCount = ref(0)

async function loadCartCount() {
  if (!userStore.token) return
  try {
    const cart = await getCart()
    cartCount.value = cart.reduce((s, i) => s + i.count, 0)
  } catch {
    /* 未联调时静默 */
  }
}

function onUserCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.replace('/')
  } else if (cmd === 'admin') {
    router.push('/admin')
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'orders') {
    router.push('/orders')
  }
}

/* ---- 轮播：用广告位自动轮播 + 指示点 ---- */
const bannerIndex = ref(0)
let bannerTimer: ReturnType<typeof setInterval> | null = null

function startBanner() {
  stopBanner()
  if (adverts.value.length < 2) return
  bannerTimer = setInterval(() => {
    bannerIndex.value = (bannerIndex.value + 1) % adverts.value.length
  }, 4200)
}

function stopBanner() {
  if (bannerTimer) {
    clearInterval(bannerTimer)
    bannerTimer = null
  }
}

function gotoBanner(i: number) {
  bannerIndex.value = i
  startBanner()
}

/** 广告 linkUrl 形如 /category/1 —— 解析为分类并重新加载商品 */
function jumpAd(linkUrl?: string) {
  if (!linkUrl) return
  const m = linkUrl.match(/\/category\/(\d+)/)
  if (m) {
    const cat = categories.value.find((c) => String(c.id) === m[1] && c.parentId === 0)
    if (cat) {
      pickCategory(cat.id)
      document.querySelector('.grid')?.scrollIntoView({ behavior: 'smooth' })
      return
    }
  }
  router.push(linkUrl)
}

async function loadCategories() {
  categories.value = await getCategories()
}

async function loadAdverts() {
  adverts.value = await getAdverts()
  startBanner()
}

const PAGE_SIZE = 12
const currentPage = ref(1)

async function loadProducts() {
  loading.value = true
  try {
    const page = await getProductList({
      page: currentPage.value,
      size: PAGE_SIZE,
      categoryId: activeCategory.value,
      keyword: keyword.value || undefined,
    })
    products.value = page.records
    total.value = page.total
  } catch {
    ElMessage.warning('商品接口暂不可用（服务未启动或未联调）')
  } finally {
    loading.value = false
  }
}

function pickCategory(id?: number | string) {
  activeCategory.value = id ? Number(id) : undefined
  currentPage.value = 1
  loadProducts()
}

function onSearch() {
  currentPage.value = 1
  loadProducts()
}

function onPageChange(p: number) {
  currentPage.value = p
  loadProducts()
  document.querySelector('.grid')?.scrollIntoView({ behavior: 'smooth' })
}

onMounted(() => {
  loadCategories()
  loadAdverts()
  loadProducts()
  loadCartCount()
  startBanner()
})
onBeforeUnmount(stopBanner)
</script>

<template>
  <div class="home">
    <header class="nav">
      <router-link class="brand" to="/">
        <span class="brand-logo">MX</span><span class="brand-sub">多商家商城</span>
      </router-link>
      <div class="search">
        <input v-model="keyword" type="search" placeholder="搜索商品 / 店铺" @keyup.enter="onSearch" />
        <button class="search-btn" @click="onSearch">搜索</button>
      </div>
      <nav class="nav-right">
        <router-link class="nav-link cart-link" to="/cart">
          <span class="cart-ico">🛒</span>
          <span class="cart-txt">购物车</span>
          <span v-if="cartCount" class="cart-badge md-num">{{ cartCount }}</span>
        </router-link>
        <template v-if="userStore.token">
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="nav-link hi">
              {{ userStore.nickname || userStore.username }} ▾
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                <el-dropdown-item v-if="userStore.type === 1 || userStore.type === 2" command="admin">工作台</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="nav-link">登录</router-link>
          <router-link to="/register" class="nav-link">注册</router-link>
        </template>
      </nav>
    </header>

    <nav class="cat-walk">
      <span
        v-for="c in categories"
        :key="c.id"
        class="cat"
        :class="{ cat_active: String(activeCategory) === String(c.id) }"
        @click="pickCategory(c.id)"
      >
        {{ c.name }}
      </span>
    </nav>

    <section v-if="adverts.length" class="banner-wrap" @mouseenter="stopBanner" @mouseleave="startBanner">
      <img
        v-for="(ad, i) in adverts"
        :key="ad.id"
        class="banner-slide"
        :class="{ banner_show: bannerIndex === i }"
        :src="ad.imgUrl"
        :alt="ad.title"
        @click="jumpAd(ad.linkUrl)"
      />
      <div class="banner-dots">
        <span
          v-for="(ad, i) in adverts"
          :key="ad.id"
          class="dot"
          :class="{ dot_active: bannerIndex === i }"
          @click="gotoBanner(i)"
        />
      </div>
    </section>

    <section v-if="adverts.length" class="advert-strip">
      <a v-for="ad in adverts" :key="ad.id" class="advert" href="#" :title="ad.title" @click.prevent="jumpAd(ad.linkUrl)">
        <img :src="ad.imgUrl" :alt="ad.title" />
      </a>
    </section>

    <section v-loading="loading" class="grid">
      <router-link v-for="p in products" :key="String(p.id)" class="card" :to="`/product/${p.id}`">
        <div class="card-img"><img :src="p.mainImg" :alt="p.title" /></div>
        <div class="card-body">
          <p class="card-title">{{ p.title }}</p>
          <p class="card-seller">
            <span class="seller-mark">MX</span> {{ p.shopName }}
          </p>
          <div class="card-foot">
            <span class="price">¥ <b class="md-num">{{ p.minPrice?.toFixed(2) }}</b></span>
            <span class="sale md-num">已售 {{ p.saleCount ?? 0 }}</span>
          </div>
        </div>
      </router-link>
      <p v-if="!loading && !products.length" class="empty">空货架 —— 商家上架后自动出现</p>
    </section>

    <div v-if="total" class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="currentPage"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px 64px;
}

/* 通栏导航 */
.nav {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 0 14px;
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
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--mx-red);
  color: #fff;
  font-family: var(--md-font-display);
  font-weight: 800;
  font-size: 18px;
}
.brand-sub {
  font-family: var(--md-font-body);
  font-weight: 500;
  font-size: 13px;
  color: var(--mx-ink-2);
}
.search {
  flex: 1;
  max-width: 460px;
  display: flex;
  overflow: hidden;
  border: 2px solid var(--mx-red);
  border-radius: 10px;
  background: #fff;
  transition: box-shadow 0.15s ease;
}
.search:focus-within {
  box-shadow: 0 0 0 3px var(--mx-red-soft);
}
.search input {
  flex: 1;
  border: none;
  outline: none;
  padding: 9px 14px;
  font-size: 14px;
  font-family: var(--md-font-body);
  color: var(--mx-ink);
}
.search-btn {
  padding: 0 20px;
  border: none;
  background: var(--mx-red);
  color: #fff;
  font-size: 14px;
  font-family: var(--md-font-body);
  cursor: pointer;
}
.search-btn:hover {
  background: var(--mx-red-deep);
}
.nav-right {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-left: auto;
}
.nav-link {
  font-size: 14px;
  color: var(--mx-ink-2);
}
.nav-link:hover {
  color: var(--mx-red);
}
.hi {
  color: var(--mx-ink);
  font-weight: 600;
}
.cart-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
}
.cart-ico {
  font-size: 17px;
}
.cart-txt {
  font-size: 14px;
}
.cart-badge {
  display: inline-block;
  margin-left: 2px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--mx-red);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
}

/* 类目走廊 */
.cat-walk {
  display: flex;
  gap: 10px;
  padding: 14px 0 16px;
  flex-wrap: wrap;
}
.cat {
  padding: 7px 16px;
  background: var(--mx-bg-2);
  border-radius: 8px;
  font-size: 13px;
  color: var(--mx-ink);
  cursor: pointer;
  transition: all 0.12s ease;
}
.cat:hover {
  color: var(--mx-red);
  background: var(--mx-red-soft);
}
.cat_active {
  background: var(--mx-red) !important;
  color: #fff !important;
  font-weight: 600;
}

/* 大轮播 */
.banner-wrap {
  position: relative;
  height: 300px;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 16px;
  background: var(--mx-bg-2);
}
.banner-slide {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transform: scale(1.02);
  transition: opacity 0.5s ease, transform 0.5s ease;
  cursor: pointer;
}
.banner_show {
  opacity: 1;
  transform: none;
}
.banner-dots {
  position: absolute;
  bottom: 12px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 8px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.06);
  cursor: pointer;
}
.dot_active {
  background: #fff;
  width: 22px;
}

/* 广告条（小卡横排） */
.advert-strip {
  display: flex;
  gap: 12px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}
.advert {
  flex: 1;
  min-width: 200px;
  height: 84px;
  border-radius: 10px;
  overflow: hidden;
  position: relative;
  display: block;
}
.advert img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s ease;
}
.advert:hover img {
  transform: scale(1.04);
}

/* 商品网格 5 列 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  gap: 16px;
}
.card {
  border: 1px solid var(--mx-line);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  transition: box-shadow 0.16s ease, transform 0.16s ease;
}
.card:hover {
  box-shadow: 0 6px 22px rgba(29, 33, 41, 0.1);
  transform: translateY(-2px);
}
.card-img {
  aspect-ratio: 4 / 3;
  background: var(--mx-bg-2);
}
.card-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.card-body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--mx-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
.card-seller {
  margin: 0;
  font-size: 12px;
  color: var(--mx-ink-2);
  display: flex;
  align-items: center;
  gap: 5px;
}
.seller-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background: var(--mx-red);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}
.card-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.price {
  color: var(--mx-red);
  font-size: 13px;
  font-weight: 600;
}
.price b {
  font-size: 20px;
  font-weight: 700;
}
.sale {
  font-size: 12px;
  color: var(--mx-ink-2);
}
.empty {
  grid-column: 1 / -1;
  text-align: center;
  color: var(--mx-ink-2);
  font-size: 14px;
  padding: 40px 0;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 860px) {
  .nav {
    flex-wrap: wrap;
  }
  .search {
    order: 3;
    min-width: 100%;
  }
  .banner-wrap {
    height: 210px;
  }
}
</style>
