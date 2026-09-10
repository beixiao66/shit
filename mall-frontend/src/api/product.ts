import http from './http'

/** 商品接口：前台浏览（/api/portal/** 游客）+ 商家管理（/api/product/** type=2） */
export interface Product {
  id: number
  merchantId: number
  categoryId: number
  title: string
  subtitle?: string
  mainImg: string
  minPrice?: number
  shopName?: string
  status: number
  saleCount: number
}

export interface Sku {
  id?: number
  specJson: string
  price: number
  stock: number
  status: number
  remark?: string
}

export interface ProductDetail extends Product {
  detail?: string
  createTime?: string
  skus: Sku[]
}

export interface ProductForm {
  id?: number
  categoryId: number
  title: string
  subtitle?: string
  mainImg: string
  detail?: string
  status: number
  skus: Sku[]
}

interface Page<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface ProductQuery {
  page?: number
  size?: number
  categoryId?: number | string
  merchantId?: number | string
  keyword?: string
}

/** 店铺信息（前台店铺页头部） */
export interface ShopInfo {
  merchantId: number | string
  shopName: string
  shopLogo?: string
  shopDesc?: string
  shopAddress?: string
  shopStatus?: number
}

// 前台
export const getProductList = (query: ProductQuery = {}) =>
  http.get<never, Page<Product>>('/portal/products', { params: query })
export const getProductDetail = (id: number | string) => http.get<never, ProductDetail>(`/portal/products/${id}`)
export const getShopInfo = (merchantId: number | string) =>
  http.get<never, ShopInfo>(`/portal/shops/${merchantId}`)

// 商家管理（商品管理页面）
export const getMyProducts = (page = 1, size = 10) =>
  http.get<never, Page<Product>>('/product/list', { params: { page, size } })
export const saveProduct = (data: ProductForm) =>
  data.id ? http.put(`/product/${data.id}`, data) : http.post('/product', data)
export const updateProductStatus = (id: number, status: number) =>
  http.put(`/product/${id}/status`, null, { params: { status } })
export const deleteProduct = (id: number) => http.delete(`/product/${id}`)

/** 商品图片上传（MinIO，返回公开 URL；数据库存该 URL） */
export const uploadProductImage = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return http.post<never, { url: string }>('/product/upload/image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
