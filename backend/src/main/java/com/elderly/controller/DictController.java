package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.entity.SysDict;
import com.elderly.entity.SysDictItem;
import com.elderly.service.impl.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统字典（管理端维护，字典项接口登录用户均可读）
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /** 字典类型列表 */
    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<SysDict>> types() {
        return Result.success(dictService.listTypes());
    }

    /** 字典项（按编码） */
    @GetMapping("/items")
    public Result<List<SysDictItem>> items(@RequestParam String code) {
        return Result.success(dictService.listItems(code));
    }

    @PostMapping("/type/save")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> saveType(@RequestBody SysDict dict) {
        dictService.saveType(dict);
        return Result.success();
    }

    @DeleteMapping("/type/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.success();
    }

    @PostMapping("/item/save")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> saveItem(@RequestBody SysDictItem item) {
        dictService.saveItem(item);
        return Result.success();
    }

    @DeleteMapping("/item/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.success();
    }
}
