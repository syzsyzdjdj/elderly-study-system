package com.elderly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import lombok.Data;

/**
 * 语音合成请求
 */
@Data
public class TtsDTO {

    @NotBlank(message = "语音文本不能为空")
    @Size(max = 20000, message = "语音文本不能超过20000字，请分篇朗读")
    private String text;

    /** 语速(可选，默认由服务端决定) */
    @DecimalMin("0.5")
    @DecimalMax("2.0")
    private Double speed;

    /** 音色(可选：default/elderly/female/male1/male/stable，缺省默认音色) */
    private String voice;
}
