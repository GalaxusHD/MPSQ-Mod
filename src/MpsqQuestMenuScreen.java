package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The designed quest board; selecting a quest opens the full interactive quest screen. */
public final class MpsqQuestMenuScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 256;
    private static final int GRID_X = 15, GRID_Y = 106, SLOT = 36, COLUMNS = 9, ROWS = 3;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_menu.png");
    private static final Identifier CHEST_CLOSED = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_reward_closed.png");
    private static final Identifier CHEST_OPEN = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_reward_open.png");

    private static final Identifier PLUS = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/gui/cinematic_add.png");
    private boolean editMode, pending;
    private int page, request;
    private final net.minecraft.client.multiplayer.ClientLevel originWorld = net.minecraft.client.Minecraft.getInstance().level;
    static boolean canEdit() { return TeamStateStore.self().map(p -> p.permissionRank().level() >= TeamRank.OFFICER.level()).orElse(false); }
    private boolean editable() { return canEdit() && minecraft.level == originWorld && !pending; }
    private final Screen parent;
    private final String npcId;
    private float drawScale;
    private int drawLeft, drawTop;
    private String category = "standard";
    private String status = "Quests werden geladen …";
    private JsonArray quests = new JsonArray();
    private boolean rewardClaimed;

    public MpsqQuestMenuScreen(Screen parent, String npcId) {
        super(Component.literal("Quest"));
        this.parent = parent;
        this.npcId = npcId;
    }

    private boolean localWorld() { return MpsqActionSync.server().isBlank() && MpsqLocalWorldStore.available(); }

    @Override protected void init() {
        updateLayout();
        int generation = ++request;
        quests = new JsonArray();
        if (!canEdit()) editMode = false;
        if (localWorld()) {
            for (JsonElement e : MpsqLocalWorldStore.array("quests")) {
                if (e.isJsonObject() && npcId.equals(str(e.getAsJsonObject(), "npc_id", ""))) quests.add(e.deepCopy());
            }
            status = quests.isEmpty() ? "Noch keine Quests vorhanden." : "";
            return;
        }
        String path = "/npcs/" + npcId + "/quests?server=" + enc(MpsqActionSync.server()) + "&world=" + enc(MpsqActionSync.world());
        MpsqApiClient.get(path).whenComplete((data, error) -> minecraft.execute(() -> {
            if (generation != request || minecraft.level != originWorld) return;
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

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        updateLayout();
        int drawWidth = Math.round(ART_WIDTH * drawScale);
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, drawLeft, drawTop, 0, 0, drawWidth, Math.round(250 * drawScale), ART_WIDTH, 250, ART_WIDTH, 250);
        // The supplied texture ends at the footer. Complete its six-pixel lower frame.
        fillArt(context, 0, 250, 352, 6, 0xFF000000);
        fillArt(context, 2, 250, 348, 4, 0xFF555555);
        fillArt(context, 3, 250, 346, 2, 0xFFC6C6C6);
        List<JsonObject> entries = visibleQuests();
        int capacity = COLUMNS * ROWS;
        rewardClaimed = false;
        page = Math.min(page, Math.max(0, (entries.size() + (editMode && canEdit() ? 1 : 0) - 1) / capacity));
        int start = page * capacity;
        for (int i = 0; i < Math.min(capacity, entries.size() - start); i++) {
            JsonObject q = entries.get(start + i);
            drawItem(context, str(q, "icon_item", "minecraft:paper"), i % COLUMNS, i / COLUMNS);
            rewardClaimed |= q.has("claimed") && q.get("claimed").getAsBoolean();
        }
        if (editMode && canEdit() && entries.size() >= start && entries.size() < start + capacity) {
            int slot = entries.size() - start;
            int px = drawLeft + Math.round((GRID_X + slot % COLUMNS * SLOT + 3) * drawScale);
            int py = drawTop + Math.round((GRID_Y + slot / COLUMNS * SLOT + 3) * drawScale);
            int size = Math.max(1, Math.round(30 * drawScale));
            context.blit(RenderPipelines.GUI_TEXTURED, PLUS, px, py, 0, 0, size, size, 32, 32, 32, 32);
        }
        if (canEdit()) {
            drawItemAt(context, new ItemStack(Items.BRUSH), 237, 216);
            if (editMode) fillArt(context, 237, 242, 22, 2, 0xFF55FF55);
        }
        if (entries.size() + (editMode && canEdit() ? 1 : 0) > capacity) {
            context.text(font, "‹ " + (page + 1) + " ›", drawLeft + Math.round(65 * drawScale), drawTop + Math.round(228 * drawScale), 0xFFFFFFFF);
        }
        Identifier chest = rewardClaimed ? CHEST_OPEN : CHEST_CLOSED;
        int chestX = drawLeft + Math.round(160 * drawScale), chestY = drawTop + Math.round(216 * drawScale), chestSize = Math.max(1, Math.round(32 * drawScale));
        context.blit(RenderPipelines.GUI_TEXTURED, chest, chestX, chestY, 0, 0, chestSize, chestSize, 32, 32, 32, 32);
        double mx = (mouseX - drawLeft) / drawScale, my = (mouseY - drawTop) / drawScale;
        String tip = null;
        boolean customDescription=false;
        if (canEdit() && inside(mx,my,237,216,30,33)) tip = "Editiermodus: " + (editMode ? "AN · Klick zum Ausschalten" : "AUS · Klick zum Einschalten");
        else if (inside(mx,my,GRID_X,GRID_Y,COLUMNS*SLOT,ROWS*SLOT)) {
            int index = page*capacity + (int)(my-GRID_Y)/SLOT*COLUMNS + (int)(mx-GRID_X)/SLOT;
            if (index < entries.size()) {
                tip = str(entries.get(index),"title","Quest") + (editMode && canEdit() ? " · Linksklick: bearbeiten · Rechtsklick: löschen" : " · Linksklick: öffnen");
                if(!editMode)customDescription=MpsqDescriptionTooltips.draw(context,"quest",str(entries.get(index),"id",""),mouseX,mouseY,width,height);
            }
            else if (index == entries.size() && editMode && canEdit()) tip = "Quest hinzufügen";
        }
        if (tip != null&&!customDescription) context.setComponentTooltipForNextFrame(font,List.of(MpsqRichText.parse(tip)),mouseX,mouseY);
        if (!entries.isEmpty() && !status.isBlank() && !status.equals("Noch keine Quests vorhanden.")) context.centeredText(font,font.plainSubstrByWidth(status,Math.max(100,width-16)),width/2,Math.max(0,drawTop-10),0xFFFFAAAA);
        if (entries.isEmpty()) {
            context.pose().pushMatrix();
            context.pose().translate(drawLeft, drawTop);
            context.pose().scale(drawScale, drawScale);
            var lines = font.split(Component.literal(status.isBlank()?"Noch keine Quests vorhanden.":status), COLUMNS * SLOT - 16);
            int y = 160 - (lines.size() - 1) * font.lineHeight / 2;
            for (var line : lines) { context.text(font, line, (ART_WIDTH - font.width(line))/2, y, 0xFFFFFFFF); y += font.lineHeight; }
            context.pose().popMatrix();
        }
    }

    /** Shared by rendering and input, including the first frame after a resize. */
    private void updateLayout() {
        drawScale = Math.max(0.001f, Math.min(1.0f, Math.min(Math.max(1, width - 32.0f) / ART_WIDTH, Math.max(1, height - 32.0f) / ART_HEIGHT)));
        drawLeft = (width - Math.round(ART_WIDTH * drawScale)) / 2;
        drawTop = (height - Math.round(ART_HEIGHT * drawScale)) / 2;
    }

    private void drawItem(GuiGraphicsExtractor context,String itemId,int col,int row){
        MpsqGuiIcon.draw(context,itemId,drawLeft+Math.round((GRID_X+col*SLOT+3)*drawScale),drawTop+Math.round((GRID_Y+row*SLOT+3)*drawScale),Math.max(1,Math.round(30*drawScale)));
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        updateLayout();
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if ((button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) || drawScale <= 0 || minecraft.level != originWorld) return super.mouseClicked(event, doubled);
        double x = (mouseX - drawLeft) / drawScale, y = (mouseY - drawTop) / drawScale;
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(x, y, 237, 216, 30, 33) && editable()) { editMode = !editMode; return true; }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(x, y, 60, 216, 50, 33)) { int maxPage=Math.max(0,(visibleQuests().size()+(editMode&&canEdit()?1:0)-1)/27); page=Math.max(0,Math.min(maxPage,page+(x<85?-1:1))); return true; }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(x, y, 51, 72, 105, 34)) { page = 0; category = "standard"; return true; }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(x, y, 195, 72, 106, 34)) { page = 0; category = "event"; return true; }
        if (inside(x, y, GRID_X, GRID_Y, COLUMNS * SLOT, ROWS * SLOT)) {
            int col = (int)(x - GRID_X) / SLOT, row = (int)(y - GRID_Y) / SLOT, index = page * COLUMNS * ROWS + row * COLUMNS + col;
            List<JsonObject> entries = visibleQuests();
            if (index >= 0 && index < entries.size()) {
                JsonObject quest = entries.get(index);
                if (editMode && editable()) {
                    if (button == InputConstants.MOUSE_BUTTON_LEFT) minecraft.gui.setScreen(MpsqQuestsScreen.editor(this, npcId, quest, category));
                    else confirmDelete(quest);
                } else if (button == InputConstants.MOUSE_BUTTON_LEFT) minecraft.gui.setScreen(new MpsqQuestsScreen(this, npcId, str(quest, "id", "")));
            }
            else if (button == InputConstants.MOUSE_BUTTON_LEFT && index == entries.size() && editMode && editable()) minecraft.gui.setScreen(MpsqQuestsScreen.editor(this, npcId, null, category));
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(x, y, 15, 216, 34, 33)) { onClose(); return true; }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(x, y, 307, 213, 34, 36)) { minecraft.gui.setScreen(new MpsqCollectionTutorialScreen(this, "Quests")); return true; }
        return true;
    }

    private void fillArt(GuiGraphicsExtractor c, int x, int y, int w, int h, int color) {
        c.fill(drawLeft + Math.round(x * drawScale), drawTop + Math.round(y * drawScale), drawLeft + Math.round((x+w) * drawScale), drawTop + Math.round((y+h) * drawScale), color);
    }
    private void drawItemAt(GuiGraphicsExtractor c, ItemStack item, int x, int y) {
        c.pose().pushMatrix(); c.pose().translate(drawLeft + x * drawScale, drawTop + y * drawScale); c.pose().scale(drawScale*1.875f, drawScale*1.875f); c.item(item, 0, 0); c.pose().popMatrix();
    }
    private void confirmDelete(JsonObject quest) {
        minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok -> {
            if (!ok || !editable()) { minecraft.gui.setScreen(this); return; }
            String id = str(quest, "id", "");
            if (localWorld()) {
                JsonArray next = new JsonArray();
                for (JsonElement e : MpsqLocalWorldStore.array("quests")) if (!e.isJsonObject() || !id.equals(str(e.getAsJsonObject(), "id", "")) || !npcId.equals(str(e.getAsJsonObject(), "npc_id", ""))) next.add(e);
                boolean saved = MpsqLocalWorldStore.setArray("quests", next); minecraft.gui.setScreen(this);
                if (!saved) status = "Löschen fehlgeschlagen.";
            } else {
                pending = true; JsonObject body = new JsonObject(); body.addProperty("server", MpsqActionSync.server()); body.addProperty("world", MpsqActionSync.world()); body.addProperty("questId", id); body.addProperty("action", "delete");
                minecraft.gui.setScreen(this);
                MpsqApiClient.post("/npcs/" + npcId + "/quests", body).whenComplete((data, error) -> minecraft.execute(() -> {
                    pending = false; if (minecraft.level != originWorld) return;
                    if (error == null) init(); else status = "Löschen fehlgeschlagen: " + error.getMessage();
                }));
            }
        }, Component.literal("Quest löschen?"), Component.literal("„" + str(quest, "title", "Quest") + "“ wird dauerhaft gelöscht. Dies kann nicht rückgängig gemacht werden.")) {
            @Override public void onClose() { minecraft.gui.setScreen(MpsqQuestMenuScreen.this); }
        });
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int pages = Math.max(1, (visibleQuests().size() + (editMode && canEdit() ? 1 : 0) + 26) / 27);
        page = Math.max(0, Math.min(pages - 1, page - (int)Math.signum(vertical))); return true;
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String str(JsonObject o, String key, String fallback) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback; }
    private static boolean inside(double x, double y, int left, int top, int w, int h) { return x >= left && x < left + w && y >= top && y < top + h; }
    @Override public void onClose() { if (minecraft != null) minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
