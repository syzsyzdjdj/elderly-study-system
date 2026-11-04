package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 积分获取规则
 */
@Data
@TableName("point_rule")
public class PointRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码 CHECKIN/COURSE/QA */
    private String ruleCode;

    private String ruleName;

    /** 每次奖励积分 */
    private Integer points;

    /** 每日上限次数 0不限 */
    private Integer dailyLimit;

    private Integer enabled;

    private String remark;
}
