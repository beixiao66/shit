<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, RefreshLeft, Service, Top, Van } from '@element-plus/icons-vue'
import { getAdverts, getCategories, type Advert, type Category } from '@/api/catalog'
import { getProductList, searchShops, type Product, type ShopInfo } from '@/api/product'
import { getCart } from '@/api/cart'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const categories = ref<Category[]>([])
const adverts = ref<Advert[]>([])
const products = ref<Product[]>([])
/** 热销推荐（按销量倒序取前 8） */
const hotProducts = ref<Product[]>([])
/** 相关店铺（搜索时按店铺名匹配，可直接进店） */
const shops = ref<ShopInfo[]>([])
const total = ref(0)
const loading = ref(false)
const activeCategory = ref<number>()
const keyword = ref('')
const cartCount = ref(0)
/** 回到顶部按钮显隐 */
const showTop = ref(false)

/** 服务保障条（静态展示，放在原广告条位置） */
const SERVICES = [
  { icon: CircleCheck, title: '正品保障', sub: '入驻商家实名审核' },
  { icon: Van, title: '极速发货', sub: '下单后 48 小时内发出' },
  { icon: RefreshLeft, title: '7 天无理由', sub: '签收后 7 天内可退' },
  { icon: Service, title: '售后无忧', sub: '平台介入处理纠纷' },
]

async function loadHot() {
  try {
    const page = await getProductList({ page: 1, size: 6, sort: 'sale' })
    hotProducts.value = page.records
  } catch {
    /* 热销区非关键路径，失败静默 */
  }
}

function onScroll() {
  showTop.value = window.scrollY > 600
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

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

async function loadCategories() {
  categories.value = await getCategories()
}

async function loadAdverts() {
  adverts.value = await getAdverts()
  startBanner()
}

const pageSize = ref(12)
const currentPage = ref(1)

async function loadProducts() {
  loading.value = true
  try {
    const page = await getProductList({
      page: currentPage.value,
      size: pageSize.value,
      categoryId: activeCategory.value,
      keyword: keyword.value.trim() || undefined,
    })
    products.value = page.records
    total.value = Number(page.total)
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

/** 搜索商品名/店铺名：重置类目与页码并滚到结果区（结果区在轮播图下方，不滚动会像"没反应"） */
function onSearch() {
  activeCategory.value = undefined
  currentPage.value = 1
  loadProducts()
  loadShops()
  document.querySelector('.grid')?.scrollIntoView({ behavior: 'smooth' })
}

/** 相关店铺：搜索词命中店铺名时展示，点击直接进店；未搜索/无命中则不展示 */
async function loadShops() {
  const kw = keyword.value.trim()
  if (!kw) {
    shops.value = []
    return
  }
  try {
    shops.value = await searchShops(kw)
  } catch {
    shops.value = []
  }
}

function onPageChange(p: number) {
  currentPage.value = p
  loadProducts()
  document.querySelector('.grid')?.scrollIntoView({ behavior: 'smooth' })
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadProducts()
}

onMounted(() => {
  loadCategories()
  loadAdverts()
  loadProducts()
  loadHot()
  loadCartCount()
  startBanner()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => {
  stopBanner()
  window.removeEventListener('scroll', onScroll)
})
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
        <router-link v-if="userStore.token" class="nav-link" to="/orders">我的订单</router-link>
        <template v-if="userStore.token">
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="nav-link hi">
              {{ userStore.nickname || userStore.username }} ▾
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
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
        class="cat"
        :class="{ cat_active: activeCategory === undefined }"
        @click="pickCategory(undefined)"
      >
        查看全部
      </span>
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

    <section class="promise">
      <div v-for="s in SERVICES" :key="s.title" class="promise-item">
        <el-icon class="promise-icon" :size="26"><component :is="s.icon" /></el-icon>
        <div class="promise-text">
          <p class="promise-title">{{ s.title }}</p>
          <p class="promise-sub">{{ s.sub }}</p>
        </div>
      </div>
    </section>

    <section v-if="hotProducts.length && !keyword.trim()" class="hot">
      <div class="section-head">
        <h2 class="section-title">热销好物</h2>
        <span class="section-sub">按销量排序</span>
      </div>
      <div class="hot-grid">
        <router-link v-for="p in hotProducts" :key="`hot-${p.id}`" class="card" :to="`/product/${p.id}`">
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
      </div>
    </section>

    <section v-if="shops.length" class="shop-hits">
      <div class="section-head">
        <h2 class="section-title">相关店铺</h2>
        <span class="section-sub">点击进店查看该店全部商品</span>
      </div>
      <div class="shop-hit-grid">
        <router-link v-for="s in shops" :key="s.merchantId" class="shop-hit" :to="`/shop/${s.merchantId}`">
          <span class="shop-hit-logo">
            <img v-if="s.shopLogo" :src="s.shopLogo" :alt="s.shopName" />
            <span v-else class="shop-hit-fallback">MX</span>
          </span>
          <span class="shop-hit-info">
            <span class="shop-hit-name">{{ s.shopName }}</span>
            <span class="shop-hit-desc">{{ s.shopDesc || '暂无店铺简介' }}</span>
          </span>
          <span class="shop-hit-enter">进店 ›</span>
        </router-link>
      </div>
    </section>

    <div class="section-head all-head">
      <h2 class="section-title">{{ keyword.trim() ? `「${keyword.trim()}」的搜索结果` : '全部商品' }}</h2>
    </div>

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
      <p v-if="!loading && !products.length" class="empty">
        {{ keyword.trim() ? `没有找到「${keyword.trim()}」相关商品，换个关键词试试` : '空货架 —— 商家上架后自动出现' }}
      </p>
    </section>

    <div v-if="total" class="pager">
      <el-pagination
        background
        layout="prev, pager, next, sizes, total"
        :total="total"
        :page-size="pageSize"
        :page-sizes="[12, 24, 36]"
        :current-page="currentPage"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <footer class="foot">
      <div class="foot-cols">
        <div class="foot-col">
          <p class="foot-title">Mall-X 多商家商城</p>
          <p class="foot-text">
            多商家入驻的高并发电商平台。用户可浏览选购、下单支付、跟踪物流并确认收货；
            商家可提交入驻、经营店铺、处理发货与提现；平台负责类目、广告位与商家审核。
          </p>
        </div>
        <div class="foot-col">
          <p class="foot-title">商家服务</p>
          <p class="foot-text">
            在线提交入驻申请，审核通过后维护商品、规格与库存，处理店铺订单发货，
            查看本店营收看板，申请余额提现 —— 经营状况一目了然。
          </p>
        </div>
        <div class="foot-col">
          <p class="foot-title">平台保障</p>
          <p class="foot-text">
            入驻商家实名审核，订单全链路状态可追溯；下单幂等、库存缓存与数据库双重扣减、
            支付回调验签，为每一笔交易保驾护航。
          </p>
        </div>
      </div>
      <p class="foot-copy">© 2026 Mall-X 多商家商城 · 教学实训项目 · 仅用于演示</p>
    </footer>

    <button v-show="showTop" class="to-top" title="回到顶部" @click="scrollTop">
      <el-icon :size="18"><Top /></el-icon>
    </button>
  </div>
</template>

<style scoped>
.home {
  max-width: 1440px;
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

/* 服务保障条（原广告条位置） */
.promise {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 22px;
}
.promise-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid var(--mx-line);
  border-radius: 10px;
  background: #fff;
}
.promise-icon {
  color: var(--mx-red);
  flex-shrink: 0;
}
.promise-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--mx-ink);
}
.promise-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--mx-ink-2);
}

/* 区块标题（热销 / 全部商品） */
.section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 26px 0 14px;
}
.all-head {
  margin-top: 30px;
}
.section-title {
  margin: 0;
  font-family: var(--md-font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--mx-ink);
}
.section-sub {
  font-size: 12px;
  color: var(--mx-ink-2);
}

/* 热销推荐：与下方"全部商品"同尺寸同列数（一行的卡片大小完全一致） */
.hot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  gap: 16px;
}

/* 相关店铺（搜索结果直达店铺页） */
.shop-hits {
  margin-top: 26px;
}
.shop-hit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}
.shop-hit {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--mx-line);
  border-radius: 10px;
  background: #fff;
  transition: all 0.15s ease;
}
.shop-hit:hover {
  border-color: var(--mx-red);
  box-shadow: 0 4px 14px rgba(29, 33, 41, 0.08);
}
.shop-hit-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--mx-bg-2);
}
.shop-hit-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.shop-hit-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: var(--mx-red);
  color: #fff;
  font-weight: 800;
  font-size: 15px;
}
.shop-hit-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.shop-hit-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--mx-ink);
}
.shop-hit-desc {
  font-size: 12px;
  color: var(--mx-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-hit-enter {
  margin-left: auto;
  font-size: 13px;
  color: var(--mx-red);
  white-space: nowrap;
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

/* 页脚 */
.foot {
  margin-top: 48px;
  padding-top: 26px;
  border-top: 1px solid var(--mx-line);
}
.foot-cols {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
}
.foot-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--mx-ink);
}
.foot-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--mx-ink-2);
}
.foot-copy {
  margin: 26px 0 0;
  padding-top: 16px;
  border-top: 1px solid var(--mx-line);
  font-size: 12px;
  text-align: center;
  color: var(--mx-ink-2);
}

/* 回到顶部 */
.to-top {
  position: fixed;
  right: 32px;
  bottom: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid var(--mx-line);
  background: #fff;
  color: var(--mx-ink);
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(29, 33, 41, 0.12);
  transition: all 0.15s ease;
  z-index: 20;
}
.to-top:hover {
  border-color: var(--mx-red);
  color: var(--mx-red);
  transform: translateY(-2px);
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
  .promise,
  .hot-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .foot-cols {
    grid-template-columns: 1fr;
    gap: 18px;
  }
}
</style>
