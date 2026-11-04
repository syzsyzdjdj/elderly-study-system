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
 * 学习趋势结果（Spark 任务3）
 */
@Data
@TableName("analysis_study_trend")
public class AnalysisStudyTrend {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学习日期 */
    private LocalDate studyDate;

    /** 当日独立活跃学习用户数 */
    private Long studyCount;

    /** 总学习时长(分钟) */
    private Long studyDurationMin;

    /** 分析时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
