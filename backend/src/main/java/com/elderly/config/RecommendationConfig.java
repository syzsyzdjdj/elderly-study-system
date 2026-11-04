package com.elderly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 推荐覆盖策略的运行门槛，不代表推荐效果已经通过验证。 */
@Data
@Component
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationConfig {
    private int limit = 6;
    private int lookbackDays = 90;
    private int minUsers = 5;
    private int minPositivePairs = 20;
    private int minUserCourses = 2;
    private int maxModelAgeDays = 30;
}
