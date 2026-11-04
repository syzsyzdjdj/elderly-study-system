package com.elderly.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.entity.CourseCategory;
import com.elderly.entity.CourseInfo;

import java.util.List;
import java.util.Map;

/**
 * 课程服务（分类 + 课程）
 */
public interface CourseService {

    /** 分类树 */
    List<CourseCategory> listCategoryTree();

    /** 新增分类 */
    void addCategory(CourseCategory category);

    /** 修改分类 */
    void updateCategory(CourseCategory category);

    /** 删除分类(存在子分类或课程时拒绝) */
    void deleteCategory(Long id);

    /**
     * 自动分类：基于课程标题/简介关键词匹配，将未分类课程归入对应分类。
     * @return {total=扫描课程数, matched=成功匹配数, unmatched=未匹配数}
     */
    Map<String, Integer> autoCategorize();

    /**
     * 课程分页列表(带分类名)
     * @param province 省份过滤（非空时返回全国通用课程 province IS NULL + 该省课程）
     * @param city     城市过滤（在省份基础上进一步保留全国通用 + 本市课程）
     */
    Page<CourseInfo> pageCourses(long page, long size, Long categoryId, String keyword,
                                 Integer status, String province, String city);

    /** 新增课程 */
    void addCourse(CourseInfo course);

    /** 修改课程 */
    void updateCourse(CourseInfo course);

    /** 删除课程(存在学习记录时拒绝) */
    void deleteCourse(Long id);

    /** 课程详情(附带当前用户学习进度) */
    Map<String, Object> detail(Long id);
}
