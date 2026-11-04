package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分明细
 */
@Data
@TableName("point_record")
public class PointRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 积分变动（正=获得 负=消耗） */
    private Integer changeAmount;

    /** 变动后余额 */
    private Integer balance;

    /** CHECKIN/COURSE/QA/EXCHANGE/ADMIN */
    private String changeType;

    private String remark;

    private Long refId;

    private LocalDateTime createTime;
}
