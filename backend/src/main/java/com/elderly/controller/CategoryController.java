package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.entity.CourseCategory;
import com.elderly.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程分类管理（写操作需 ADMIN 或 COURSE_MANAGER）
 */
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CourseService courseService;

    /** 分类树 */
    @GetMapping("/list")
    public Result<List<CourseCategory>> list() {
        return Result.success(courseService.listCategoryTree());
    }

    /** 新增分类 */
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> add(@RequestBody CourseCategory category) {
        courseService.addCategory(category);
        return Result.success();
    }

    /** 修改分类 */
    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> update(@RequestBody CourseCategory category) {
        courseService.updateCategory(category);
        return Result.success();
    }

    /** 删除分类 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.deleteCategory(id);
        return Result.success();
    }

    /**
     * 一键自动分类：基于课程标题/简介关键词匹配，将课程归入对应分类。
     * 按 course-category-rules.json 中的分类名称路径匹配，不依赖分类主键。
     */
    @PostMapping("/auto-categorize")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<java.util.Map<String, Integer>> autoCategorize() {
        return Result.success(courseService.autoCategorize());
    }
}
