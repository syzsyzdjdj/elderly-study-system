package com.elderly.spark;

import org.apache.spark.ml.linalg.Vector;

/** 模型概率未经校准；通过证据、类别差距与词汇覆盖门槛后才展示。 */
public final class ClassificationConfidence {
    public static final String UNKNOWN = "未确定业务";
    private ClassificationConfidence() {}

    public static Double accepted(Vector features, Vector probabilities, Double vocabularyCoverage) {
        if (features == null || features.numNonzeros() == 0 || probabilities == null || probabilities.size() < 2
                || vocabularyCoverage == null || !Double.isFinite(vocabularyCoverage) || vocabularyCoverage < 0.5) return null;
        double best = 0, second = 0, total = 0;
        for (double p : probabilities.toArray()) {
            if (!Double.isFinite(p) || p < 0 || p > 1) return null;
            total += p;
            if (p > best) { second = best; best = p; } else if (p > second) second = p;
        }
        if (Math.abs(total - 1) > 1e-6) return null;
        return best >= 0.6 && best - second >= 0.15 ? best : null;
    }
}
