package de.galaxushd.mpsqcamera;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Whitelist policy for blocks that can host an MPSQ redstone action. */
public final class MpsqTriggerBlockPolicy {
    public enum Kind {
        BUTTON("Knopf", false), LEVER("Hebel", true), PRESSURE_PLATE("Druckplatte", true),
        SCULK_SENSOR("Sculk-Sensor", false), TRIPWIRE("Faden / Stolperdraht", false), NONE("Nicht unterstützt", false);
        private final String label;
        private final boolean followPowerState;
        Kind(String label, boolean followPowerState) { this.label = label; this.followPowerState = followPowerState; }
        public String label() { return label; }
        public boolean usesPowerEdge() { return this != NONE; }
        public boolean followsPowerState() { return followPowerState; }
    }

    private MpsqTriggerBlockPolicy() { }

    public static Kind classify(BlockState state, LevelReader world, BlockPos pos) {
        if (state == null || state.isAir()) return Kind.NONE;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.endsWith("_button")) return Kind.BUTTON;
        if (path.equals("lever") || path.endsWith("_lever")) return Kind.LEVER;
        if (path.endsWith("_pressure_plate")) return Kind.PRESSURE_PLATE;
        if (path.equals("sculk_sensor") || path.endsWith("_sculk_sensor")) return Kind.SCULK_SENSOR;
        if (path.equals("tripwire") || path.equals("tripwire_hook") || path.endsWith("_tripwire_hook")) return Kind.TRIPWIRE;
        return Kind.NONE;
    }

    public static boolean isActivated(BlockState state, Kind kind) {
        if (kind == Kind.SCULK_SENSOR) {
            for (var property : state.getProperties()) {
                String name = property.getName();
                if (name.contains("phase") && String.valueOf(state.getValue(property)).equalsIgnoreCase("active")) return true;
                if ("power".equals(name) && state.getValue(property) instanceof Number power && power.intValue() > 0) return true;
            }
            return false;
        }
        for (var property : state.getProperties()) {
            if (!"powered".equals(property.getName())) continue;
            Object value = state.getValue(property);
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    public static String describeProperties(BlockState state) {
        if (state.getProperties().isEmpty()) return "(keine Zustandswerte)";
        java.util.List<String> entries = new java.util.ArrayList<>();
        for (var property : state.getProperties()) entries.add(property.getName() + " = " + state.getValue(property));
        java.util.Collections.sort(entries);
        return String.join("  ·  ", entries);
    }
}
