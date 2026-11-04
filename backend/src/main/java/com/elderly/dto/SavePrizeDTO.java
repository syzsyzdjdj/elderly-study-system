package com.elderly.dto;

import com.elderly.entity.Prize;
import jakarta.validation.constraints.*;

/** 管理端可写字段；时间戳由服务端维护。零积分奖品允许兑换。 */
public record SavePrizeDTO(
        @Positive Long id,
        @NotBlank(message = "奖品名称不能为空") @Size(max = 100) String name,
        @Size(max = 255) String cover,
        @PositiveOrZero(message = "兑换积分不能为负数") Integer pointsCost,
        @PositiveOrZero(message = "库存不能为负数") Integer stock,
        String description,
        @Min(0) @Max(1) Integer status) {
    public Prize toEntity() {
        Prize prize = new Prize();
        prize.setId(id);
        prize.setName(name.trim());
        prize.setCover(cover);
        prize.setPointsCost(pointsCost == null ? 0 : pointsCost);
        prize.setStock(stock == null ? 0 : stock);
        prize.setDescription(description);
        prize.setStatus(status == null ? 1 : status);
        return prize;
    }
}
