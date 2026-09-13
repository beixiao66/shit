<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createEmployee, getEmployees, updateEmployeeStatus, type EmployeeRow } from '@/api/user'

const list = ref<EmployeeRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const dialog = ref(false)
const form = reactive({ username: '', password: '', nickname: '', phone: '' })

async function load() {
  const res = await getEmployees(page.value, pageSize.value)
  list.value = res.records
  total.value = res.total
}

/** 切换每页条数：回到第 1 页重查 */
function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  load()
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('用户名/密码必填')
    return
  }
  await createEmployee({ ...form })
  ElMessage.success('员工已创建（初始密码登录后可修改，账号禁用/启用由状态开关控制）')
  dialog.value = false
  Object.assign(form, { username: '', password: '', nickname: '', phone: '' })
  load()
}

async function toggle(row: EmployeeRow) {
  const next = row.status === 0 ? 1 : 0
  await ElMessageBox.confirm(`${next === 1 ? '禁用' : '启用'}员工「${row.username}」？`, '提醒', { type: 'warning' })
  await updateEmployeeStatus(row.id, next)
  ElMessage.success('已更新')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1 class="title">员工管理</h1>
      <el-button type="primary" @click="dialog = true">新增员工</el-button>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="账号" width="160" />
      <el-table-column prop="nickname" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '在职' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="180">
        <template #default="{ row }">
          <span class="md-num">{{ (row.createTime ?? '').replace('T', ' ').slice(0, 19) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button link :type="row.status === 0 ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.status === 0 ? '禁用' : '启用' }}
          </el-button>
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

    <el-dialog v-model="dialog" title="新增员工" width="420">
      <el-form label-width="70px">
        <el-form-item label="账号">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">创建</el-button>
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
</style>
