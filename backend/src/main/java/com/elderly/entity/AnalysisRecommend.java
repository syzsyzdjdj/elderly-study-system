package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个性化课程推荐结果（Spark 任务6，Item-CF）
 */
@Data
@TableName("analysis_recommend")
public class AnalysisRecommend {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 课程ID */
    private Long courseId;

    /** 课程标题 */
    private String title;

    /** 推荐得分 */
    private Double score;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
