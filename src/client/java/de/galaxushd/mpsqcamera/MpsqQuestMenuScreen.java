package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The designed quest board; selecting a quest opens the full interactive quest screen. */
public final class MpsqQuestMenuScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 250;
    private static final int GRID_X = 15, GRID_Y = 106, SLOT = 36, COLUMNS = 9, ROWS = 3;
    private static final Identifier BACKGROUND = Identifier.of(MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_menu.png");
    private static final Identifier CHEST_CLOSED = Identifier.of(MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_reward_closed.png");
    private static final Identifier CHEST_OPEN = Identifier.of(MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_reward_open.png");

    private final Screen parent;
    private final String npcId;
    private float drawScale;
    private int drawLeft, drawTop;
    private String category = "standard";
    private String status = "Quests werden geladen …";
    private JsonArray quests = new JsonArray();
    private boolean rewardClaimed;

    public MpsqQuestMenuScreen(Screen parent, String npcId) {
        super(Text.literal("Quest"));
        this.parent = parent;
        this.npcId = npcId;
    }

    private boolean localWorld() { return MpsqActionSync.server().isBlank() && MpsqLocalWorldStore.available(); }

    @Override protected void init() {
        if (localWorld()) {
            for (JsonElement e : MpsqLocalWorldStore.array("quests")) {
                if (e.isJsonObject() && npcId.equals(str(e.getAsJsonObject(), "npc_id", ""))) quests.add(e.deepCopy());
            }
            status = quests.isEmpty() ? "Noch keine Quests vorhanden." : "";
            return;
        }
        String path = "/npcs/" + npcId + "/quests?server=" + enc(MpsqActionSync.server()) + "&world=" + enc(MpsqActionSync.world());
        MpsqApiClient.get(path).whenComplete((data, error) -> client.execute(() -> {
            if (error == null && data != null && data.isJsonArray()) {
                quests = data.getAsJsonArray();
                status = quests.isEmpty() ? "Noch keine Quests vorhanden." : "";
            } else status = "Quests konnten nicht geladen werden.";
        }));
    }

    private List<JsonObject> visibleQuests() {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement e : quests) if (e.isJsonObject()) {
            JsonObject q = e.getAsJsonObject();
            String type = str(q, "category", str(q, "quest_type", "standard"));
            boolean event = type.equalsIgnoreCase("event") || (q.has("is_event") && q.get("is_event").getAsBoolean());
            if (event == category.equals("event")) result.add(q);
        }
        return result;
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawScale = Math.max(0.1f, Math.min(1.0f, Math.min((width - 16.0f) / ART_WIDTH, (height - 16.0f) / ART_HEIGHT)));
        int drawWidth = Math.round(ART_WIDTH * drawScale), drawHeight = Math.round(ART_HEIGHT * drawScale);
        drawLeft = (width - drawWidth) / 2; drawTop = (height - drawHeight) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND, drawLeft, drawTop, 0, 0, drawWidth, drawHeight, ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);
        List<JsonObject> entries = visibleQuests();
        int capacity = COLUMNS * ROWS;
        rewardClaimed = false;
        for (int i = 0; i < Math.min(capacity, entries.size()); i++) {
            JsonObject q = entries.get(i);
            drawItem(context, str(q, "icon_item", "minecraft:paper"), i % COLUMNS, i / COLUMNS);
            rewardClaimed |= q.has("claimed") && q.get("claimed").getAsBoolean();
        }
        Identifier chest = rewardClaimed ? CHEST_OPEN : CHEST_CLOSED;
        int chestX = drawLeft + Math.round(160 * drawScale), chestY = drawTop + Math.round(216 * drawScale), chestSize = Math.max(1, Math.round(32 * drawScale));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, chest, chestX, chestY, 0, 0, chestSize, chestSize, 32, 32, 32, 32);
        if (entries.isEmpty()) {
            int cx = drawLeft + Math.round(176 * drawScale), cy = drawTop + Math.round(160 * drawScale);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, cy, 0xFFFFFFFF);
        }
    }

    private void drawItem(DrawContext context, String itemId, int col, int row) {
        try {
            Identifier id = Identifier.of(itemId);
            var item = Registries.ITEM.get(id);
            if (item != Items.AIR) {
                int x = drawLeft + Math.round((GRID_X + col * SLOT + 9) * drawScale);
                int y = drawTop + Math.round((GRID_Y + row * SLOT + 9) * drawScale);
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(drawScale, drawScale, 1.0f);
                context.drawItem(new ItemStack(item), 0, 0);
                context.getMatrices().pop();
            }
        } catch (RuntimeException ignored) { }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || drawScale <= 0) return super.mouseClicked(mouseX, mouseY, button);
        double x = (mouseX - drawLeft) / drawScale, y = (mouseY - drawTop) / drawScale;
        if (inside(x, y, 51, 72, 105, 34)) { category = "standard"; return true; }
        if (inside(x, y, 195, 72, 106, 34)) { category = "event"; return true; }
        if (inside(x, y, GRID_X, GRID_Y, COLUMNS * SLOT, ROWS * SLOT)) {
            int col = (int)(x - GRID_X) / SLOT, row = (int)(y - GRID_Y) / SLOT, index = row * COLUMNS + col;
            List<JsonObject> entries = visibleQuests();
            if (index >= 0 && index < entries.size()) {
                String id = str(entries.get(index), "id", "");
                client.setScreen(new MpsqQuestsScreen(this, npcId, id));
            }
            return true;
        }
        if (inside(x, y, 15, 216, 34, 33)) { close(); return true; }
        if (inside(x, y, 307, 213, 34, 36)) { client.setScreen(new MpsqCollectionTutorialScreen(this, "Quests")); return true; }
        return true;
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String str(JsonObject o, String key, String fallback) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback; }
    private static boolean inside(double x, double y, int left, int top, int w, int h) { return x >= left && x < left + w && y >= top && y < top + h; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
