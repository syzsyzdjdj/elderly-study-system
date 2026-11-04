package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 奖品兑换记录
 */
@Data
@TableName("prize_exchange")
public class PrizeExchange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long prizeId;

    /** 奖品名称快照 */
    private String prizeName;

    private Integer pointsCost;

    /** PENDING待发放 DONE已发放 CANCEL已取消 */
    private String status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime handleTime;

    private Long handlerId;

    /** 兑换人昵称(非表字段) */
    @TableField(exist = false)
    private String userName;
}
