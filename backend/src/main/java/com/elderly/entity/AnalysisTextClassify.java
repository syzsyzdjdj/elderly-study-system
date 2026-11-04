package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答文本分类结果（Spark 任务8）：地区 × 业务类别 双维度问答量统计
 */
@Data
@TableName("analysis_text_classify")
public class AnalysisTextClassify {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 省份（未绑定地区为"未知地区"） */
    private String province;

    /** 预测业务类别（课程顶级分类名） */
    private String category;

    /** 该地区该类别的问答数量 */
    private Long questionCount;

    /** 已接受分类的平均模型概率；未确定及旧批次为null，不代表准确率。 */
    private Double avgConfidence;

    private Double minConfidence;

    /** 已接受分类中概率>=0.8的提问数；旧批次为null。 */
    private Long highConfidenceCount;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
