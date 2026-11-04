package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程热度排行结果（Spark 任务2）
 */
@Data
@TableName("analysis_course_hot")
public class AnalysisCourseHot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名次 */
    private Integer rankNo;

    /** 课程ID */
    private Long courseId;

    /** 课程标题 */
    private String title;

    /** 窗口内课程独立学习用户数（不是日志/会话次数） */
    private Long studyCount;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
