package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习记录
 */
@Data
@TableName("study_record")
public class StudyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 课程ID */
    private Long courseId;

    /** 学习进度(0-100，百分比) */
    private Integer progress;

    /** 是否完成(0否 1是) */
    private Integer isFinish;

    /** 完成积分是否已发放(0否 1是) */
    private Integer awarded;

    /** 累计学习时长(分钟) */
    private Integer studyTime;

    /** 最近学习时间 */
    private LocalDateTime lastStudyTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 课程标题(非表字段，列表填充用) */
    @TableField(exist = false)
    private String courseTitle;

    /** 课程封面(非表字段，列表填充用) */
    @TableField(exist = false)
    private String cover;

    /** 用户昵称(非表字段，课程反向查看时填充) */
    @TableField(exist = false)
    private String userName;
}
