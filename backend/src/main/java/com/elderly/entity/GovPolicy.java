package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 政务政策库
 */
@Data
@TableName("gov_policy")
public class GovPolicy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String source;

    /** NATIONAL全国 PROVINCE省级 CITY市级 */
    private String policyLevel;

    private String province;

    private String city;

    private String govUrl;

    private LocalDate publishTime;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private java.time.LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private java.time.LocalDateTime updateTime;
}
