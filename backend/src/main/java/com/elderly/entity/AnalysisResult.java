package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学情聚类分析结果
 */
@Data
@TableName("analysis_result")
public class AnalysisResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 簇编号 */
    private Integer clusterId;

    /** 相对活跃度组名（相对高活跃组/相对低活跃组） */
    private String clusterName;

    /** 用户数 */
    private Integer userCount;

    /** 组内用户活跃日平均累计分钟的均值 */
    private Double avgStudyTime;

    /** 平均完成率(0-1) */
    private Double avgFinishRate;

    /** 描述 */
    private String description;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
