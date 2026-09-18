import http from './http'

/** 支付接口（/api/pay；支付单创建、Mock 回调、状态查询、退款）+ 管理员退款记录 */
export interface RefundRecord {
  id: number
  refundNo: string
  payNo: string
  orderNo: string
  amount: number
  reason?: string
  status: number
  handleTime?: string
  createTime: string
}

/**
 * 创建支付单的返回凭证。
 * realChannel=false 时是本地演示渠道（Mock），前端展示"模拟支付"按钮；
 * realChannel=true 时是真实支付宝沙箱，前端应跳转 payUrl 到支付宝收银台。
 */
export interface PayCreateResult {
  payNo: string
  orderNo: string
  /** 金额字符串（两位小数），避免浮点误差 */
  amount: string | null
  /** 支付跳转地址：沙箱=支付宝网关；Mock=本系统演示页 */
  payUrl: string | null
  realChannel: boolean
}

export const createPay = (orderNo: string) =>
  http.post<never, PayCreateResult>('/pay/create', { orderNo })

/** 查询支付凭证（不重复下单） */
export const getPayInfo = (orderNo: string) => http.get<never, PayCreateResult | null>(`/pay/info/${orderNo}`)

/** 演示用：模拟渠道回调（真实沙箱由支付宝异步通知 /pay/alipay/notify 触发） */
export const mockPayCallback = (payNo: string, tradeNo: string, amount: number) =>
  http.post('/pay/mock/callback', { payNo, tradeNo, amount })

export const getPayStatus = (orderNo: string) => http.get<never, boolean>(`/pay/status/${orderNo}`)

export const requestRefund = (orderNo: string, reason: string) =>
  http.post<never, string>('/pay/refund', { orderNo, reason })

export const getRefunds = (status?: number, page = 1, size = 10) =>
  http.get<never, { records: RefundRecord[]; total: number; current: number; size: number }>('/admin/refunds', {
    params: { status, page, size },
  })
