package com.elderly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.CourseInfo;
import com.elderly.service.AnalysisService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 课程管理（列表/详情公开可看；写操作需 ADMIN 或 COURSE_MANAGER）
 */
@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final AnalysisService analysisService;

    /** 课程分页列表（公开可看，带分类名；传 province/city 时按"全国通用+本地"分层过滤） */
    @GetMapping("/list")
    public Result<Page<CourseInfo>> list(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String province,
                                         @RequestParam(required = false) String city) {
        return Result.success(courseService.pageCourses(page, size, categoryId, keyword, status, province, city));
    }

    /** 混合推荐：内容相似 + Spark 排序 + 热门补位，附实际来源和推荐原因。 */
    @GetMapping("/recommend")
    public Result<List<CourseInfo>> recommend() {
        return Result.success(analysisService.recommendCourses(SecurityUtil.getCurrentUserId()));
    }

    /** 课程详情（公开可看，附带当前用户学习进度，未登录为 null） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(courseService.detail(id));
    }

    /** 新增课程 */
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> add(@RequestBody CourseInfo course) {
        courseService.addCourse(course);
        return Result.success();
    }

    /** 修改课程 */
    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> update(@RequestBody CourseInfo course) {
        courseService.updateCourse(course);
        return Result.success();
    }

    /** 删除课程 */
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return Result.success();
    }
}
