package dev.swarmmobs.ai;

public record SwarmStrategyDecision(
        Mode mode,
        double formationRadiusMultiplier,
        double separationMultiplier,
        double cohesionMultiplier,
        double searchRadiusMultiplier,
        String rationale,
        String providerId
) {
    public enum Mode {
        BASELINE,
        ENCIRCLE,
        CONCENTRATE,
        REGROUP,
        SEARCH
    }

    public static SwarmStrategyDecision baseline(String providerId, String rationale) {
        return new SwarmStrategyDecision(
                Mode.BASELINE,
                1.0,
                1.0,
                1.0,
                1.0,
                rationale,
                providerId
        );
    }

    public SwarmStrategyDecision sanitized() {
        return new SwarmStrategyDecision(
                mode == null ? Mode.BASELINE : mode,
                clamp(formationRadiusMultiplier, 0.70, 1.50),
                clamp(separationMultiplier, 0.70, 1.50),
                clamp(cohesionMultiplier, 0.70, 1.50),
                clamp(searchRadiusMultiplier, 0.80, 1.40),
                rationale == null ? "" : rationale.substring(0, Math.min(240, rationale.length())),
                providerId == null ? "unknown" : providerId
        );
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(min, Math.min(max, value));
    }
}
