package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 定时语音提醒
 */
@Data
@TableName("reminder")
public class Reminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 提醒标题，如"学习打卡" */
    private String title;

    /** 语音播报文本 */
    private String content;

    /** 重复类型: DAILY/WEEKLY/MONTHLY/ONCE */
    private String remindType;

    /** 提醒时间 HH:mm:ss */
    private LocalTime remindTime;

    /** 周提醒用: 1-7(周一到周日) */
    private Integer dayOfWeek;

    /** 月提醒用: 1-31 */
    private Integer dayOfMonth;

    /** 下次触发时间(服务端计算) */
    private LocalDateTime nextTriggerTime;

    /** 是否开启: 1=开启 0=关闭 */
    private Integer enabled;

    /** 今日是否已触发过 */
    @TableField(exist = false)
    private Boolean triggeredToday;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
