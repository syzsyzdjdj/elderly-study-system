package com.elderly.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学习进度上报请求
 */
@Data
public class StudyRecordDTO {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 本次学习进度(0-100，服务端取与历史记录中的较大值) */
    private Integer progress;

    /** 本次学习时长(分钟，累加到累计时长) */
    private Integer studyTime;
}
