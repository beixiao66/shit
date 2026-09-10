<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteProduct, getMyProducts, saveProduct, updateProductStatus, uploadProductImage, type Product, type Sku } from '@/api/product'
import { getCategories, type Category } from '@/api/catalog'
import ImageUpload from '@/components/ImageUpload.vue'

const list = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const categories = ref<Category[]>([])
const dialog = ref(false)
const editingId = ref<number>()

interface SpecPair {
  key: string
  value: string
}
/** 带可视化规格维度对的可编辑 SKU */
type SkuEdit = Sku & { pairs: SpecPair[] }

const form = reactive({
  categoryId: 0,
  title: '',
  subtitle: '',
  mainImg: '',
  detail: '',
  status: 0,
  skus: [] as SkuEdit[],
})

async function load() {
  const res = await getMyProducts(page.value, 10)
  list.value = res.records
  total.value = res.total
}

/** 类目 → 关联规格名（选择类目后，规格名仅显示该类目相关的项） */
const CATEGORY_SPEC_KEYS: Record<string, string[]> = {
  手机数码: ['容量', '内存', '颜色', '版本'],
  手机通讯: ['颜色', '版本', '内存', '容量'],
  家用电器: ['容量', '功率', '颜色', '版本'],
  服饰鞋帽: ['颜色', '尺码', '材质'],
  男装: ['颜色', '尺码', '版型'],
}
const DEFAULT_SPEC_KEYS = ['颜色', '尺码', '版本', '容量']

/** 当前类目关联的规格名 */
const availableSpecKeys = computed(() => {
  const name = categories.value.find((c) => c.id === form.categoryId)?.name ?? ''
  return CATEGORY_SPEC_KEYS[name] ?? DEFAULT_SPEC_KEYS
})
/** 各规格名的常用值，供下拉选择；也允许自定义输入 */
const SPEC_VALUES: Record<string, string[]> = {
  颜色: ['红色', '橙色', '黄色', '绿色', '蓝色', '紫色', '黑色', '白色', '灰色', '粉色'],
  尺码: ['S', 'M', 'L', 'XL', 'XXL', '均码', '40', '41', '42', '43'],
  版本: ['标准版', '豪华版', '套装'],
  容量: ['64G', '128G', '256G', '512G', '1T'],
  内存: ['4G', '6G', '8G', '12G', '16G'],
  功率: ['500W', '800W', '1000W', '1500W', '2000W'],
  材质: ['棉', '涤纶', '真丝', '羊毛', '皮革'],
  版型: ['修身', '宽松', '直筒', '均码'],
  规格: ['默认'],
}
/** 颜色值 → 色块，直观展示 */
const COLOR_HEX: Record<string, string> = {
  红色: '#e53935',
  橙色: '#fb8c00',
  黄色: '#fdd835',
  绿色: '#43a047',
  蓝色: '#1e88e5',
  紫色: '#8e24aa',
  黑色: '#212121',
  白色: '#ffffff',
  灰色: '#9e9e9e',
  粉色: '#ec407a',
}

function valueOptions(key: string): string[] {
  return SPEC_VALUES[key] ?? []
}

function colorHex(key: string, value: string): string | undefined {
  return key === '颜色' ? COLOR_HEX[value] : undefined
}

/** specJson → 规格维度对（用于可视化编辑） */
function pairsFromJson(json: string): SpecPair[] {
  try {
    const obj = JSON.parse(json) as Record<string, string>
    const pairs = Object.entries(obj).map(([key, value]) => ({ key, value }))
    return pairs.length ? pairs : [{ key: '颜色', value: '' }]
  } catch {
    return [{ key: '颜色', value: '' }]
  }
}

function defaultSku(): SkuEdit {
  const firstKey = availableSpecKeys.value[0] ?? '颜色'
  return { specJson: '', price: 0, stock: 0, status: 0, remark: '', pairs: [{ key: firstKey, value: '' }] }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    categoryId: categories.value[0]?.id ?? 0,
    title: '',
    subtitle: '',
    mainImg: '',
    detail: '',
    status: 0,
  })
  form.skus = [defaultSku()]
  dialog.value = true
}

/** 切换类目：清理与该类目不相关的规格维度，保证规格与类目关联 */
function onCategoryChange() {
  const allowed = availableSpecKeys.value
  form.skus.forEach((sku) => {
    sku.pairs = sku.pairs.filter((p) => !p.key || allowed.includes(p.key))
    if (!sku.pairs.length) sku.pairs = [{ key: allowed[0] ?? '', value: '' }]
  })
}

async function openEdit(row: Product) {
  const detail = await import('@/api/product').then((m) => m.getProductDetail(row.id))
  editingId.value = row.id
  Object.assign(form, {
    categoryId: row.categoryId,
    title: row.title,
    subtitle: row.subtitle ?? '',
    mainImg: row.mainImg,
    detail: detail.detail ?? '',
    status: row.status,
  })
  form.skus = detail.skus.length ? detail.skus.map((s) => ({ ...s, pairs: pairsFromJson(s.specJson) })) : [defaultSku()]
  dialog.value = true
}

async function submit() {
  for (const s of form.skus) {
    if (!s.pairs.some((p) => p.key && p.value)) {
      ElMessage.warning('每个规格 SKU 至少填写一个规格维度')
      return
    }
  }
  const skus = form.skus.map((s) => {
    const spec: Record<string, string> = {}
    s.pairs.forEach((p) => {
      if (p.key && p.value) spec[p.key] = p.value
    })
    return {
      id: s.id,
      specJson: JSON.stringify(spec),
      price: s.price,
      stock: s.stock,
      status: s.status,
      remark: s.remark,
    }
  })
  await saveProduct({ id: editingId.value, ...form, skus })
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function toggle(row: Product) {
  await updateProductStatus(row.id, row.status === 0 ? 1 : 0)
  ElMessage.success(row.status === 0 ? '已下架' : '已上架')
  load()
}

async function remove(row: Product) {
  await ElMessageBox.confirm(`删除商品「${row.title}」（连同规格）？`, '提醒', { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  categories.value = await getCategories()
  load()
})
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">商品管理</h1>
      <el-button type="primary" @click="openCreate">新建商品</el-button>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="商品名" />
      <el-table-column label="最低价" width="120">
        <template #default="{ row }"><span class="md-num">¥ {{ row.minPrice?.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column prop="saleCount" label="销量" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '在售' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 0 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 0 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      class="pager"
      layout="prev, pager, next, total"
      :total="total"
      @current-change="load"
    />

    <el-dialog v-model="dialog" :title="editingId ? '编辑商品' : '新建商品'" width="760">
      <el-form label-width="80px">
        <el-form-item label="商品名">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" />
        </el-form-item>
        <el-form-item label="类目">
          <el-select v-model="form.categoryId" style="width: 100%" @change="onCategoryChange">
            <el-option v-for="c in categories" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="主图">
          <ImageUpload v-model="form.mainImg" :upload="uploadProductImage" placeholder="或粘贴图片 URL" />
        </el-form-item>
        <el-form-item label="图文详情">
          <el-input v-model="form.detail" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="规格 SKU">
          <div class="sku-editor">
            <div v-for="(sku, i) in form.skus" :key="i" class="sku-card">
              <div class="sku-card-head">
                <span class="sku-card-title">规格 SKU {{ i + 1 }}</span>
                <el-button link type="danger" :disabled="form.skus.length <= 1" @click="form.skus.splice(i, 1)">
                  删除
                </el-button>
              </div>

              <div class="spec-pairs">
                <div v-for="(p, j) in sku.pairs" :key="j" class="spec-pair">
                  <el-select
                    v-model="p.key"
                    class="spec-key"
                    placeholder="规格名"
                    filterable
                    default-first-option
                  >
                    <el-option v-for="k in availableSpecKeys" :key="k" :label="k" :value="k" />
                  </el-select>
                  <span class="spec-colon">：</span>
                  <el-select
                    v-model="p.value"
                    class="spec-val"
                    placeholder="规格值"
                    filterable
                    allow-create
                    default-first-option
                  >
                    <el-option v-for="v in valueOptions(p.key)" :key="v" :label="v" :value="v">
                      <span
                        v-if="colorHex(p.key, v)"
                        class="opt-color"
                        :style="{ background: colorHex(p.key, v) }"
                      />
                      <span>{{ v }}</span>
                    </el-option>
                  </el-select>
                  <el-button link type="danger" :disabled="sku.pairs.length <= 1" @click="sku.pairs.splice(j, 1)">
                    移除
                  </el-button>
                </div>
                <el-button link type="primary" @click="sku.pairs.push({ key: '', value: '' })">
                  + 添加规格维度
                </el-button>
              </div>

              <div class="sku-fields">
                <div class="sku-field">
                  <span class="field-label">价格(¥)</span>
                  <el-input-number v-model="sku.price" :precision="2" :min="0" :controls="false" />
                </div>
                <div class="sku-field">
                  <span class="field-label">库存(件)</span>
                  <el-input-number v-model="sku.stock" :min="0" :controls="false" />
                </div>
                <div class="sku-field">
                  <span class="field-label">备注</span>
                  <el-input v-model="sku.remark" placeholder="可选" />
                </div>
              </div>
            </div>

            <el-button type="primary" plain @click="form.skus.push(defaultSku())">+ 添加规格 SKU</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
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
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.sku-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sku-card {
  border: 1px solid var(--mx-line);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--mx-bg-2);
}
.sku-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sku-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--mx-ink);
}
.spec-pairs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.spec-pair {
  display: flex;
  align-items: center;
  gap: 6px;
}
.spec-key {
  width: 120px;
}
.spec-val {
  width: 190px;
}
.spec-colon {
  color: var(--mx-ink-2);
}
.opt-color {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-right: 6px;
  border-radius: 50%;
  border: 1px solid var(--mx-line);
  vertical-align: middle;
}
.sku-fields {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.sku-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-label {
  font-size: 12px;
  color: var(--mx-ink-2);
}
.sku-field :deep(.el-input-number),
.sku-field :deep(.el-input) {
  width: 150px;
}
</style>
