package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程关联规则结果（Spark 任务4，FP-Growth）
 */
@Data
@TableName("analysis_association_rule")
public class AnalysisAssociationRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 前件课程ID(逗号分隔) */
    private String antecedentIds;

    /** 前件课程标题 */
    private String antecedentTitles;

    /** 后件课程ID(逗号分隔) */
    private String consequentIds;

    /** 后件课程标题 */
    private String consequentTitles;

    /** 置信度 */
    private Double confidence;

    /** 支持度 */
    private Double support;

    private Double lift;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
