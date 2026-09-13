import http from './http'

/** 商家域接口：商家端（/api/merchant）+ 平台运营（/api/admin/merchants、/api/admin/withdrawals） */
export interface MerchantProfile {
  id: number
  merchantName: string
  contact?: string
  phone?: string
  shopName?: string
  shopLogo?: string
  shopDesc?: string
  shopAddress?: string
  shopStatus: number
  balance: number
  applyStatus: number
  status: number
}

export interface MerchantRow {
  id: number
  username: string
  merchantName: string
  contact?: string
  phone?: string
  applyStatus: number
  rejectReason?: string
  shopName?: string
  createTime: string
}

export interface Withdrawal {
  id: number
  withdrawalNo: string
  merchantId: number
  bankName: string
  accountNo: string
  holder: string
  amount: number
  status: number
  rejectReason?: string
  handleTime?: string
  createTime: string
}

interface Page<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const getShop = () => http.get<never, MerchantProfile>('/merchant/shop')
export const saveShop = (patch: Partial<MerchantProfile>) => http.put('/merchant/shop', patch)
export const applyWithdrawal = (data: { bankName: string; accountNo: string; holder: string; amount: number }) =>
  http.post('/merchant/withdrawal', data)
export const getMyWithdrawals = (page = 1, size = 10) =>
  http.get<never, Page<Withdrawal>>('/merchant/withdrawal', { params: { page, size } })

/** 图片上传（店铺 logo/收款码，MinIO 返回公开 URL，DB 存 URL） */
export const uploadMerchantImage = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return http.post<never, { url: string }>('/merchant/upload/image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const getMerchants = (applyStatus?: number, page = 1, size = 10) =>
  http.get<never, Page<MerchantRow>>('/admin/merchants', { params: { applyStatus, page, size } })
export const auditMerchant = (id: number, pass: boolean, reason?: string) =>
  http.put(`/admin/merchants/${id}/audit`, { pass, reason })

export const getWithdrawals = (status?: number, page = 1, size = 10) =>
  http.get<never, Page<Withdrawal>>('/admin/withdrawals', { params: { status, page, size } })
export const processWithdrawal = (id: number, pass: boolean, reason?: string) =>
  http.put(`/admin/withdrawals/${id}/process`, { pass, reason })
