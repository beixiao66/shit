<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getShop, saveShop, uploadMerchantImage, type MerchantProfile } from '@/api/merchant'
import ImageUpload from '@/components/ImageUpload.vue'

const profile = ref<MerchantProfile | null>(null)
const loading = ref(false)
const form = reactive({
  shopName: '',
  shopLogo: '',
  shopDesc: '',
  shopAddress: '',
})

async function load() {
  loading.value = true
  try {
    profile.value = await getShop()
    Object.assign(form, {
      shopName: profile.value.shopName ?? '',
      shopLogo: profile.value.shopLogo ?? '',
      shopDesc: profile.value.shopDesc ?? '',
      shopAddress: profile.value.shopAddress ?? '',
    })
  } finally {
    loading.value = false
  }
}

async function submit() {
  await saveShop({ ...form })
  ElMessage.success('店铺信息已保存')
  load()
}

onMounted(load)
</script>

<template>
  <div class="shop">
    <div class="head">
      <h1 class="title">店铺设置</h1>
      <span v-if="profile" class="md-num balance">
        可提现余额 ¥ {{ profile.balance.toFixed(2) }}
      </span>
    </div>

    <el-form label-width="90px" class="form" v-loading="loading">
      <el-form-item label="店铺名">
        <el-input v-model="form.shopName" />
      </el-form-item>
      <el-form-item label="店铺 Logo">
        <ImageUpload v-model="form.shopLogo" :upload="uploadMerchantImage" placeholder="或粘贴图片 URL" />
      </el-form-item>
      <el-form-item label="店铺介绍">
        <el-input v-model="form.shopDesc" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="店铺地址">
        <el-input v-model="form.shopAddress" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submit">保存</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.title {
  margin: 0;
  font-family: var(--md-font-display);
  font-size: 22px;
  font-weight: 700;
}
.balance {
  font-size: 18px;
  font-weight: 600;
  color: var(--md-color-primary);
  background: var(--md-color-accent-soft);
  padding: 8px 16px;
  border-radius: var(--md-radius-sm);
}
.form {
  max-width: 560px;
}
</style>
