package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个性化课程推荐结果（Spark 任务7，ALS 矩阵分解 / 用户-课程协同过滤）
 * 基于学习行为隐式反馈（点击、完成、进度、停留时长）由 MLlib ALS 训练预测。
 */
@Data
@TableName("analysis_recommend_als")
public class AnalysisRecommendAls {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 课程ID */
    private Long courseId;

    /** 课程标题 */
    private String title;

    /** ALS 预测偏好得分 */
    private Double score;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
