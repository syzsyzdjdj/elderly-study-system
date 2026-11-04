package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习行为日志（一次增量上报一条，不等于一次学习会话；Spark 学情分析的数据源；
 * 真实学习与模拟数据都写入此表）
 */
@Data
@TableName("study_log")
public class StudyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 课程ID */
    private Long courseId;

    /** 本次上报新增学习分钟数（不是累计时长） */
    private Integer studyTime;

    /** 学习进度(0-100) */
    private Integer progress;

    /** 是否完成(0否 1是) */
    private Integer isFinish;

    /** 学习日期 */
    private LocalDate studyDate;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 课程标题(非表字段，管理端查看明细时填充) */
    @TableField(exist = false)
    private String courseTitle;
}
