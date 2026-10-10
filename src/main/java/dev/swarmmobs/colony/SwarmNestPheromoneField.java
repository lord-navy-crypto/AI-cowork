package dev.swarmmobs.colony;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Sparse, local ant-inspired chemical analogue. No world/entity references
 * and no full-grid tick. Signals only arise from real observations or work.
 *
 * Game-scale parameters, not measured insect pheromone concentrations.
 */
public final class SwarmNestPheromoneField {
    public static final int CELL_SIZE = 4;
    public static final int MAX_CELLS = 128;
    public static final int MAX_SOURCE_DISTANCE = 28;
    public static final int HALF_LIFE_TICKS = 400;
    private static final double MAX_INTENSITY = 6.0;
    private static final double FORGET_THRESHOLD = 0.02;
    private static final double SIDE_FRACTION = 0.20;

    public record Position(int x, int y, int z) {
        public long distanceSquared(Position other) {
            long dx = (long)x - other.x;
            long dy = (long)y - other.y;
            long dz = (long)z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    private record CellKey(int x, int y, int z) {
        CellKey offset(int dx, int dy, int dz) {
            return new CellKey(x+dx, y+dy, z+dz);
        }
    }
    public enum Signal { FOOD, TIMBER, SOIL, STOP }

    private static final class Cell {
        private final double[] signals = new double[Signal.values().length];
        private long updated;
        Cell(long now) { updated = now; }
        void decay(long now) {
            if (now <= updated) return;
            double factor = Math.pow(0.5, (now - updated) / (double) HALF_LIFE_TICKS);
            for (int i = 0; i < signals.length; i++) signals[i] *= factor;
            updated = now;
        }
        double total() {
            double sum=0.0;
            for (double x:signals) sum+=x;
            return sum;
        }
    }

    private final Map<CellKey,Cell> cells = new HashMap<>();
    private int observedSites;
    private int reinforcedSites;
    private int stopSignals;

    public static Signal signal(SwarmNestColonyPolicy.Kind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case NUTRIENT -> Signal.FOOD;
            case TIMBER -> Signal.TIMBER;
            case SOIL -> Signal.SOIL;
            case NONE -> null;
        };
    }

    private static CellKey key(Position p) {
        return new CellKey(Math.floorDiv(p.x(), CELL_SIZE),
                Math.floorDiv(p.y(), CELL_SIZE), Math.floorDiv(p.z(), CELL_SIZE));
    }

    /** A scout marks a source only after sensing an actual world resource. */
    public boolean observe(Position home, Position at, SwarmNestColonyPolicy.Kind kind,
                           long now) {
        if (!valid(home,at,kind,now)) return false;
        add(key(at), signal(kind), 1.0, now);
        observedSites++;
        return true;
    }

    /** A successful physical delivery reinforces only sampled worker route cells. */
    public boolean reinforce(Position home, Position at,
                             SwarmNestColonyPolicy.Kind kind, long now) {
        return reinforce(home,at,kind,now,1.0);
    }

    /** A real successful delivery reinforces less when the dock is crowded. */
    public boolean reinforce(Position home, Position at,
                             SwarmNestColonyPolicy.Kind kind, long now,
                             double depositMultiplier) {
        if (!valid(home,at,kind,now)
                || !Double.isFinite(depositMultiplier)
                || depositMultiplier <= 0.0) return false;
        add(key(at), signal(kind),
                1.3 * Math.min(1.0,depositMultiplier),now);
        reinforcedSites++;
        return true;
    }

    /** Stalled route produces spatially targeted negative feedback. */
    public boolean inhibit(Position home, Position at,
                           SwarmNestColonyPolicy.Kind kind, long now) {
        if (!valid(home,at,kind,now)) return false;
        add(key(at), Signal.STOP, 1.2, now);
        stopSignals++;
        return true;
    }

    public double strength(Position at, Signal which, long now) {
        if (at == null || which == null || now < 0) return 0;
        Cell cell = cells.get(key(at));
        if (cell == null) return 0;
        cell.decay(now);
        return cell.signals[which.ordinal()];
    }

    /** Reads a local seven-cell neighborhood, never a distant omniscient source. */
    public double scent(Position at, Signal which, long now) {
        if (at == null || which == null || now < 0) return 0;
        CellKey k = key(at);
        double center=read(k,which,now);
        double nearby = read(k.offset(1,0,0),which,now)
                + read(k.offset(-1,0,0),which,now)
                + read(k.offset(0,0,1),which,now)
                + read(k.offset(0,0,-1),which,now)
                + read(k.offset(0,1,0),which,now)
                + read(k.offset(0,-1,0),which,now);
        return center + 0.15 * nearby;
    }

    /** Spatial smell biases, but never overrules source existence or stock capacity. */
    public double costFactor(Position at, SwarmNestColonyPolicy.Kind kind, long now) {
        Signal type = signal(kind);
        if (type == null) return 1.0;
        double attraction = scent(at,type,now);
        double danger = scent(at,Signal.STOP,now);
        return Math.max(0.55, Math.min(3.0,
                (1.0 + 0.5 * danger) / (1.0 + 0.22 * attraction)));
    }

    public int size(long now) {
        prune(now);
        return cells.size();
    }
    public int observations() { return observedSites; }
    public int reinforcements() { return reinforcedSites; }
    public int inhibitions() { return stopSignals; }

    private static boolean valid(Position home, Position at,
                                 SwarmNestColonyPolicy.Kind kind, long now) {
        return home != null && at != null && signal(kind) != null && now >= 0
                && home.distanceSquared(at) <=
                        (long) MAX_SOURCE_DISTANCE * MAX_SOURCE_DISTANCE;
    }

    private double read(CellKey key, Signal which, long now) {
        Cell cell=cells.get(key);
        if (cell == null) return 0.0;
        cell.decay(now);
        return cell.signals[which.ordinal()];
    }

    private void add(CellKey key, Signal type, double amount, long now) {
        prune(now);
        if (!cells.containsKey(key) && cells.size() >= MAX_CELLS) {
            // Preserve stronger trails, evict the weakest old hint.
            CellKey least=null;
            double intensity=Double.POSITIVE_INFINITY;
            for (var entry:cells.entrySet()) {
                double n=entry.getValue().total();
                if (n < intensity) { least=entry.getKey(); intensity=n; }
            }
            if (least != null) cells.remove(least);
        }
        Cell cell=cells.computeIfAbsent(key,ignored -> new Cell(now));
        cell.decay(now);
        cell.signals[type.ordinal()] = Math.min(MAX_INTENSITY,
                cell.signals[type.ordinal()] + amount);
        // No active neighbor diffusion: the seven-cell sensing kernel
        // represents local spreading without a per-tick world update.
    }

    private void prune(long now) {
        Iterator<Cell> it=cells.values().iterator();
        while (it.hasNext()) {
            Cell cell=it.next();
            cell.decay(now);
            if (cell.total() < FORGET_THRESHOLD) it.remove();
        }
    }
}
