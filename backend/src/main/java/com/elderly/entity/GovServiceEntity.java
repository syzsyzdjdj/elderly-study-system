package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 政务办事直达入口
 */
@Data
@TableName("gov_service")
public class GovServiceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private String serviceUrl;

    /** NATIONAL全国 LOCAL地方 */
    private String scope;

    private String province;

    private String city;

    private Integer sort;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
