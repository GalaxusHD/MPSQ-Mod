package de.galaxushd.mpsqcamera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client side trigger cache. The API remains authoritative for every fire. */
public final class MpsqTriggerManager {
    private static final List<MpsqTrigger> TRIGGERS = new CopyOnWriteArrayList<>();
    private static BlockPos lastClick;
    private static long nextPoll;
    private static String scope = "";
    private static boolean pending;
    private static int generation;

    private MpsqTriggerManager() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MpsqTriggerManager::tick);
        refresh();
    }

    public static void refresh() {
        if (pending || !MpsqApiClient.isReady() || MpsqActionSync.server().isBlank()) return;
        pending=true;
        int epoch=generation;
        MpsqApiClient.loadTriggers().whenComplete((rows,error) -> MinecraftClient.getInstance().execute(()->{
            if(epoch!=generation)return;
            pending=false;
            if(error==null){TRIGGERS.clear();TRIGGERS.addAll(rows);}
            else MpsqCameraClient.LOGGER.debug("Trigger konnten nicht geladen werden",error);
        }));
    }

    private static void tick(MinecraftClient client) {
        String current=MpsqActionSync.server()+"|"+MpsqActionSync.world();
        if(!scope.equals(current)){scope=current;generation++;pending=false;TRIGGERS.clear();lastClick=null;nextPoll=0;}
        if (client.world == null || client.player == null || !MpsqApiClient.isReady()) { lastClick = null; return; }
        long now = System.currentTimeMillis();
        if (now >= nextPoll) { nextPoll = now + 15_000L; refresh(); }
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) { lastClick = null; return; }
        BlockPos pos = hit.getBlockPos();
        if (!client.options.useKey.isPressed() || client.currentScreen != null) { lastClick = null; return; }
        if (lastClick != null && lastClick.equals(pos)) return;
        lastClick = pos;
        String worldId = client.world.getRegistryKey().getValue().toString();
        TRIGGERS.stream().filter(trigger -> trigger.worldId().equals(worldId) && trigger.position().equals(pos) && net.minecraft.registry.Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString().equals(trigger.blockId())).findFirst()
                .ifPresent(trigger -> MpsqApiClient.fireTrigger(trigger.id()).thenAccept(result -> {
                    if(!result.getAsJsonObject().has("cooldown") && "OPEN_LINK".equalsIgnoreCase(trigger.actionType())) {
                        String url=result.getAsJsonObject().getAsJsonObject("action_data").get("url").getAsString();
                        if(url.startsWith("https://"))client.execute(()->net.minecraft.client.gui.screen.ConfirmLinkScreen.open(null,url,true));
                    }
                    if (!result.getAsJsonObject().has("cooldown") && "OPEN_REDEEM".equalsIgnoreCase(trigger.actionType()))
                        client.execute(() -> client.setScreen(new MpsqRedeemScreen()));
                }).exceptionally(error -> { MpsqCameraClient.LOGGER.debug("Trigger abgelehnt", error); return null; }));
    }
}

