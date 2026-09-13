import http from './http'

/** 类目/广告接口（前台浏览 + 后台运营，对应 mall-product-service） */
export interface Category {
  id: number
  parentId: number
  name: string
  icon?: string
  sort: number
  status: number
}

/** 首页轮播广告位（纯展示，无跳转链接） */
export interface Advert {
  id: number
  title: string
  imgUrl: string
  sort: number
  status: number
}

// 前台（游客可访问）
export const getCategories = () => http.get<never, Category[]>('/portal/categories')
export const getAdverts = () => http.get<never, Advert[]>('/portal/adverts')

// 后台 /api/admin/**（平台管理员）
export const adminListCategories = () => http.get<never, Category[]>('/admin/categories')
export const saveCategory = (data: Partial<Category>) =>
  data.id ? http.put(`/admin/categories/${data.id}`, data) : http.post('/admin/categories', data)
export const deleteCategory = (id: number) => http.delete(`/admin/categories/${id}`)

export const adminListAdverts = () => http.get<never, Advert[]>('/admin/adverts')
export const saveAdvert = (data: Partial<Advert>) =>
  data.id ? http.put(`/admin/adverts/${data.id}`, data) : http.post('/admin/adverts', data)
export const deleteAdvert = (id: number) => http.delete(`/admin/adverts/${id}`)

/** 广告图上传（MinIO，平台管理员 type=1） */
export const uploadAdvertImage = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return http.post<never, { url: string }>('/admin/upload/image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
