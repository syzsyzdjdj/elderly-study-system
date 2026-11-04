package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.entity.SysDict;
import com.elderly.entity.SysDictItem;
import com.elderly.mapper.SysDictItemMapper;
import com.elderly.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统字典服务：字典类型 + 字典项维护
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper itemMapper;

    /** 全部字典类型 */
    public List<SysDict> listTypes() {
        return dictMapper.selectList(new QueryWrapper<SysDict>().orderByAsc("id"));
    }

    /** 某字典类型下的字典项（移动端/后台通用） */
    public List<SysDictItem> listItems(String dictCode) {
        return itemMapper.selectList(new QueryWrapper<SysDictItem>()
                .eq("dict_code", dictCode).eq("status", 1).orderByAsc("sort"));
    }

    public void saveType(SysDict dict) {
        if (dict.getId() == null) {
            dictMapper.insert(dict);
        } else {
            dictMapper.updateById(dict);
        }
    }

    public void deleteType(Long id) {
        SysDict dict = dictMapper.selectById(id);
        dictMapper.deleteById(id);
        if (dict != null) {
            itemMapper.delete(new QueryWrapper<SysDictItem>().eq("dict_code", dict.getDictCode()));
        }
    }

    public void saveItem(SysDictItem item) {
        if (item.getSort() == null) {
            item.setSort(0);
        }
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        if (item.getId() == null) {
            itemMapper.insert(item);
        } else {
            itemMapper.updateById(item);
        }
    }

    public void deleteItem(Long id) {
        itemMapper.deleteById(id);
    }
}
