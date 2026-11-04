import request from '../utils/request'

// 字典类型列表
export function getDictTypes() {
  return request.get('/dict/types')
}

// 字典项（按编码）
export function getDictItems(code) {
  return request.get('/dict/items', { params: { code } })
}

// 新增/修改字典类型
export function saveDictType(data) {
  return request.post('/dict/type/save', data)
}

// 删除字典类型
export function deleteDictType(id) {
  return request.delete(`/dict/type/${id}`)
}

// 新增/修改字典项
export function saveDictItem(data) {
  return request.post('/dict/item/save', data)
}

// 删除字典项
export function deleteDictItem(id) {
  return request.delete(`/dict/item/${id}`)
}
