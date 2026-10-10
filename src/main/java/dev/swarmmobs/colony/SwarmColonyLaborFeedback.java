package dev.swarmmobs.colony;

import java.util.EnumMap;

/**
 * Transient local feedback analogous to returning ant-forager encounters and
 * evaporating stigmergic cues. This is a game-scale heuristic, NOT measured
 * ant/bee kinetics. No omniscient global task scheduler.
 *
 * Success briefly reinforces an observed useful task. Failure inhibits
 * repeatedly unreachable work. Signals decay toward baseline without a
 * periodic world tick or global scan.
 */
public final class SwarmColonyLaborFeedback {
    public static final int HALF_LIFE_TICKS = 600;
    private static final double MAX_SIGNAL = 4.0;
    private static final double MAX_INHIBITION = 3.0;

    public enum Channel { FOOD, TIMBER, SOIL }

    private static final class Pulse {
        private double reinforcement;
        private double inhibition;
        private long lastTick;
        private Pulse(long now) { lastTick = now; }
    }

    private final EnumMap<Channel, Pulse> pulses = new EnumMap<>(Channel.class);
    private long successes;
    private long failures;

    public static Channel channel(SwarmNestColonyPolicy.Kind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case NUTRIENT -> Channel.FOOD;
            case TIMBER -> Channel.TIMBER;
            case SOIL -> Channel.SOIL;
            case NONE -> null;
        };
    }

    public void succeeded(SwarmNestColonyPolicy.Kind kind, int units, long now) {
        Channel channel = channel(kind);
        if (channel == null || units <= 0 || now < 0) return;
        Pulse pulse = advance(channel, now);
        pulse.reinforcement = Math.min(MAX_SIGNAL,
                pulse.reinforcement + 0.6 + 0.15 * Math.min(units, 16));
        pulse.inhibition = Math.max(0.0, pulse.inhibition - 0.4);
        successes++;
    }

    public void failed(SwarmNestColonyPolicy.Kind kind, long now) {
        Channel channel = channel(kind);
        if (channel == null || now < 0) return;
        Pulse pulse = advance(channel, now);
        pulse.inhibition = Math.min(MAX_INHIBITION, pulse.inhibition + 0.8);
        pulse.reinforcement = Math.max(0.0, pulse.reinforcement - 0.3);
        failures++;
    }

    /** Lower values mean higher priority, but never completely block work. */
    public double costFactor(SwarmNestColonyPolicy.Kind kind, long now) {
        Channel channel = channel(kind);
        if (channel == null || now < 0) return 1.0;
        Pulse pulse = advance(channel, now);
        return Math.max(0.55, Math.min(3.5,
                (1.0 + pulse.inhibition) / (1.0 + 0.35 * pulse.reinforcement)));
    }

    public double reinforcement(SwarmNestColonyPolicy.Kind kind, long now) {
        Channel channel = channel(kind);
        return channel == null || now < 0 ? 0.0 : advance(channel, now).reinforcement;
    }

    public double inhibition(SwarmNestColonyPolicy.Kind kind, long now) {
        Channel channel = channel(kind);
        return channel == null || now < 0 ? 0.0 : advance(channel, now).inhibition;
    }

    public long successes() { return successes; }
    public long failures() { return failures; }

    private Pulse advance(Channel channel, long now) {
        Pulse pulse = pulses.computeIfAbsent(channel, ignored -> new Pulse(now));
        if (now <= pulse.lastTick) return pulse;
        long elapsed = now - pulse.lastTick;
        double fraction = Math.pow(0.5, elapsed / (double) HALF_LIFE_TICKS);
        pulse.reinforcement *= fraction;
        pulse.inhibition *= fraction;
        pulse.lastTick = now;
        return pulse;
    }
}
