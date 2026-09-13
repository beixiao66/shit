<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminListAdverts, deleteAdvert, saveAdvert, uploadAdvertImage, type Advert } from '@/api/catalog'
import ImageUpload from '@/components/ImageUpload.vue'

const list = ref<Advert[]>([])
const dialog = ref(false)
const editing = ref<Advert | null>(null)
const form = reactive({ title: '', imgUrl: '', sort: 0, status: 0 })

async function load() {
  list.value = await adminListAdverts()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { title: '', imgUrl: '', sort: 0, status: 0 })
  dialog.value = true
}

function openEdit(row: Advert) {
  editing.value = row
  Object.assign(form, { title: row.title, imgUrl: row.imgUrl, sort: row.sort, status: row.status })
  dialog.value = true
}

async function submit() {
  await saveAdvert({ ...(editing.value?.id ? { id: editing.value.id } : {}), ...form })
  ElMessage.success(editing.value ? '已保存' : '已创建')
  dialog.value = false
  load()
}

async function remove(row: Advert) {
  await ElMessageBox.confirm(`删除广告位「${row.title}」？`, '提醒', { type: 'warning' })
  await deleteAdvert(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">广告管理</h1>
      <el-button type="primary" @click="openCreate">新建广告位</el-button>
    </div>
    <el-table :data="list" border>
      <el-table-column label="编码" width="90">
        <template #default="{ row }"><span class="md-num">AD-{{ String(row.id).padStart(2, '0') }}</span></template>
      </el-table-column>
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="imgUrl" label="图片" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑广告位' : '新建广告位'" width="480">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="图片">
          <ImageUpload v-model="form.imgUrl" :upload="uploadAdvertImage" placeholder="或粘贴图片 URL" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="0" :inactive-value="1" active-text="启用" inactive-text="停用" />
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
</style>
