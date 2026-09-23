package de.galaxushd.mpsqcamera;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;

public record MpsqTrigger(UUID id, String worldId, BlockPos position, String blockId,
                          String actionType, Map<String, Object> actionData) { }
