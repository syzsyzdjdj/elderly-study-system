package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 问答热点词频结果（Spark 任务5）
 */
@Data
@TableName("analysis_word_freq")
public class AnalysisWordFreq {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名次 */
    private Integer rankNo;

    /** 热点词 */
    private String word;

    /** 出现次数 */
    private Long freq;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
