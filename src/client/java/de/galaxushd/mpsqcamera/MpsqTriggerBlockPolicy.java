package de.galaxushd.mpsqcamera;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/** Whitelist policy for blocks that can host an MPSQ action. */
public final class MpsqTriggerBlockPolicy {
    public enum Kind {
        BUTTON("Knopf", true), LEVER("Hebel", true), PRESSURE_PLATE("Druckplatte", true), FULL_BLOCK("Voller Block (Rechtsklick)", false), NONE("Nicht unterstützt", false);
        private final String label;
        private final boolean redstoneSignal;
        Kind(String label, boolean redstoneSignal) { this.label = label; this.redstoneSignal = redstoneSignal; }
        public String label() { return label; }
        public boolean usesPowerEdge() { return redstoneSignal; }
    }

    private MpsqTriggerBlockPolicy() { }

    public static Kind classify(BlockState state, WorldView world, BlockPos pos) {
        if (state == null || state.isAir()) return Kind.NONE;
        String path = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
        if (path.endsWith("_button")) return Kind.BUTTON;
        if (path.equals("lever") || path.endsWith("_lever")) return Kind.LEVER;
        if (path.endsWith("_pressure_plate")) return Kind.PRESSURE_PLATE;
        return state.isFullCube(world, pos) ? Kind.FULL_BLOCK : Kind.NONE;
    }

    public static boolean isPowered(BlockState state) {
        for (var property : state.getProperties()) {
            if (!"powered".equals(property.getName())) continue;
            Object value = state.get(property);
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    public static String describeProperties(BlockState state) {
        if (state.getProperties().isEmpty()) return "(keine Zustandswerte)";
        java.util.List<String> entries = new java.util.ArrayList<>();
        for (var property : state.getProperties()) entries.add(property.getName() + " = " + state.get(property));
        java.util.Collections.sort(entries);
        return String.join("  ·  ", entries);
    }
}
