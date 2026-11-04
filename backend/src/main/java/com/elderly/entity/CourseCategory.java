package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程分类
 */
@Data
@TableName("course_category")
public class CourseCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类ID(0为顶级) */
    private Long parentId;

    /** 分类名称（对外 JSON 字段统一为 name，与前端约定一致） */
    @JsonProperty("name")
    private String categoryName;

    /** 排序 */
    private Integer sort;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 子分类(非表字段，树形返回用) */
    @TableField(exist = false)
    private List<CourseCategory> children;
}
