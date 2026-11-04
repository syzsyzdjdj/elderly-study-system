package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程信息
 */
@Data
@TableName("course_info")
public class CourseInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类ID */
    private Long categoryId;

    /** 课程标题 */
    private String title;

    /** 封面图 */
    private String cover;

    /** 课程内容 */
    private String content;

    /** 视频地址 */
    private String videoUrl;

    /** 课程简介 */
    private String intro;

    /** 预计时长(分钟) */
    private Integer duration;

    /** 完成奖励积分（对外 JSON 字段统一为 points，与前端约定一致） */
    @JsonProperty("points")
    private Integer pointsReward;

    /** 状态(0下架 1上架) */
    private Integer status;

    /** 所属省(NULL=全国通用课程) */
    private String province;

    /** 所属市 */
    private String city;

    /** 所属区县 */
    private String district;

    /** 是否已收藏(非表字段，当前用户详情填充) */
    @TableField(exist = false)
    private Boolean favorite;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 分类名称(非表字段，列表填充用) */
    @TableField(exist = false)
    private String categoryName;

    /** 推荐来源：content / als / item_cf / popular / latest，仅推荐接口填充。 */
    @TableField(exist = false)
    private java.util.List<String> recommendationSources;

    /** 可解释的推荐原因；不代表概率或已验证的推荐效果。 */
    @TableField(exist = false)
    private String recommendationReason;

    /** 推荐使用的数据来源：real / mock，仅推荐接口填充。 */
    @TableField(exist = false)
    private String recommendationDataScope;
}
