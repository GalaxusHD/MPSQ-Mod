package de.galaxushd.mpsqcamera;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record MpsqTrigger(UUID id, String worldId, BlockPos position, String blockId, String objectType,
                          String actionType, Map<String, Object> actionData) { }
