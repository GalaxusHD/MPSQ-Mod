package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Player collection view; the Accessories NPC shop remains in MpsqAccessoriesScreen. */
public class MpsqOwnedAccessoriesScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 264;
    private static final int GRID_LEFT = 14;
    private static final int GRID_TOP = 70;
    private static final int SLOT_SIZE = 36;
    private static final int GRID_COLUMNS = 7;
    private static final int GRID_ROWS = 4;
    private static final int PAGE_SIZE = GRID_COLUMNS * GRID_ROWS;
    private static final int PREVIEW_LEFT = 266;
    private static final int PREVIEW_TOP = 70;
    private static final int PREVIEW_WIDTH = 70;
    private static final int PREVIEW_HEIGHT = 108;
    private static final int ACTION_LEFT = 266;
    private static final int ACTION_TOP = 180;
    private static final int ACTION_WIDTH = 70;
    private static final int ACTION_HEIGHT = 34;
    private static final Identifier BACKGROUND = texture("mpsq_accessories_background.png");
    private static final Identifier BACKGROUND_LAST = texture("mpsq_accessories_background_last.png");
    private static final Identifier EQUIP = texture("mpsq_accessories_equip.png");
    private static final Identifier UNEQUIP = texture("mpsq_accessories_unequip.png");

    private final Screen parent;
    private final boolean shopMode;
    private JsonArray owned = new JsonArray();
    private String status = "Lade Accessoires …";
    private String selectedId;
    private String equippedId;
    private int page;
    private float drawScale;
    private int drawLeft;
    private int drawTop;
    private boolean pending;

    public MpsqOwnedAccessoriesScreen(Screen parent) {
        this(parent,false);
    }
    public MpsqOwnedAccessoriesScreen(Screen parent,boolean shopMode){
        super(Component.literal(shopMode?"Accessoire-Shop":"Accessoires"));
        this.parent=parent;this.shopMode=shopMode;
    }

    private static Identifier texture(String filename) {
        return Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/gui/" + filename);
    }

    private boolean localWorld() {
        return MpsqActionSync.server().isBlank() && MpsqLocalWorldStore.available();
    }

    @Override
    protected void init() {
        if (localWorld()) {
            owned = shopMode ? LocalMpsqBackend.accessoryCatalog() : localOwned();
            updateEquippedFromRows();
            status = owned.isEmpty() ? "Du besitzt noch keine Accessoires." : "";
            return;
        }
        MpsqApiClient.get(shopMode ? "/accessory-catalog" : "/me/accessories").whenComplete((data, error) -> minecraft.execute(() -> {
            if (minecraft.gui.screen() != this) return;
            if (error == null && data != null && data.isJsonArray()) {
                owned = LocalMpsqBackend.withBuiltinAccessories(withoutLegacyDiscordHead(data.getAsJsonArray()));
                updateEquippedFromRows();
                status = owned.isEmpty() ? "Du besitzt noch keine Accessoires." : "";
                clampPage();
            } else {
                owned=LocalMpsqBackend.withBuiltinAccessories(new JsonArray());status = "Lokale Accessoires - Serverliste konnte nicht geladen werden.";
            }
        }));
    }

    private JsonArray localOwned() {
        JsonArray result = new JsonArray();
        String equipped = MpsqLocalWorldStore.equipped();
        JsonArray catalog = LocalMpsqBackend.accessoryCatalog();
        for(var item:catalog)if(item.isJsonObject())MpsqLocalWorldStore.ownAccessory(str(item.getAsJsonObject(),"accessory_id",""));
        for (JsonElement element : MpsqLocalWorldStore.array("accessories_owned")) {
            JsonObject row = element.isJsonObject() ? element.getAsJsonObject().deepCopy() : new JsonObject();
            String id = element.isJsonObject() ? str(row, "accessory_id", "") : element.getAsString();
            if (id.isBlank()) continue;
            row.addProperty("accessory_id", id);
            row.addProperty("equipped", id.equals(equipped));
            for (JsonElement definition : catalog) {
                if (!definition.isJsonObject()) continue;
                JsonObject def = definition.getAsJsonObject();
                if (!id.equals(str(def, "accessory_id", ""))) continue;
                for (var entry : def.entrySet()) {
                    if (!row.has(entry.getKey())) row.add(entry.getKey(), entry.getValue().deepCopy());
                }
                break;
            }
            if (!isLegacyDiscordHead(row)) result.add(row);
        }
        return MpsqAccessoryRenderer.uniqueAccessories(result);
    }

    private int pageCount() {
        return Math.max(1, (owned.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void clampPage() {
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    private void updateEquippedFromRows() {
        equippedId = null;
        for (JsonElement element : owned) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (row.has("equipped") && row.get("equipped").getAsBoolean()) {
                equippedId = accessoryId(row);
                break;
            }
        }
        if (selectedId == null && equippedId != null) selectedId = equippedId;
    }

    private JsonObject itemAt(int index) {
        int absoluteIndex = page * PAGE_SIZE + index;
        if (index < 0 || index >= PAGE_SIZE || absoluteIndex >= owned.size()) return null;
        JsonElement element = owned.get(absoluteIndex);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonObject selectedItem() {
        if (selectedId == null) return null;
        for (JsonElement element : owned) {
            if (element.isJsonObject() && selectedId.equals(accessoryId(element.getAsJsonObject()))) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private int stateIndex() {
        if (selectedId == null) return 0;
        return selectedId.equals(equippedId) ? 2 : 1;
    }

    private String previewUrl(JsonObject row) {
        if (row == null) return null;
        String builtin = MpsqAccessoryRenderer.resolveBuiltinAccessory(row);
        if (builtin != null) return builtin;
        JsonObject definition = row.has("mpsq_accessories") && row.get("mpsq_accessories").isJsonObject()
                ? row.getAsJsonObject("mpsq_accessories") : row;
        String url = str(row, "url", str(definition, "url", ""));
        String assetId = str(row, "asset_id", str(definition, "asset_id", str(definition, "model_id", "")));
        String filename = str(row, "filename", str(definition, "filename", ""));
        String filenameBuiltin = MpsqAccessoryRenderer.builtinAccessoryUrl(filename);
        if (filenameBuiltin != null) return filenameBuiltin;
        return MpsqAccessoryRenderer.assetPreviewUrl(assetId, url);
    }

    private static JsonArray withoutLegacyDiscordHead(JsonArray input) {
        JsonArray filtered = new JsonArray();
        for (JsonElement value : input) {
            if (value.isJsonObject() && !isLegacyDiscordHead(value.getAsJsonObject())) filtered.add(value.deepCopy());
        }
        return MpsqAccessoryRenderer.uniqueAccessories(filtered);
    }

    private static boolean isLegacyDiscordHead(JsonObject row) { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        clampPage();
        drawScale = Math.min(1.0f, Math.min((width - 16.0f) / ART_WIDTH, (height - 16.0f) / ART_HEIGHT));
        drawScale = Math.max(0.1f, drawScale);
        int drawWidth = Math.round(ART_WIDTH * drawScale);
        int drawHeight = Math.round(ART_HEIGHT * drawScale);
        drawLeft = (width - drawWidth) / 2;
        drawTop = (height - drawHeight) / 2;

        boolean hasNextPage = page < pageCount() - 1;
        context.blit(RenderPipelines.GUI_TEXTURED, hasNextPage ? BACKGROUND : BACKGROUND_LAST,
                drawLeft, drawTop, 0, 0, drawWidth, drawHeight,
                ART_WIDTH, ART_HEIGHT, ART_WIDTH, 442);

        if (selectedId != null && !shopMode) {
            context.blit(RenderPipelines.GUI_TEXTURED, selectedId.equals(equippedId) ? UNEQUIP : EQUIP,
                    drawLeft + Math.round(ACTION_LEFT * drawScale), drawTop + Math.round(ACTION_TOP * drawScale),
                    ACTION_LEFT, ACTION_TOP, Math.round(ACTION_WIDTH * drawScale), Math.round(ACTION_HEIGHT * drawScale),
                    ACTION_WIDTH, ACTION_HEIGHT, 352, 250);
        }
        if(shopMode){
            context.centeredText(font,"Accessoire-Shop · "+MpsqLocalWorldStore.points()+" Punkte",width/2,drawTop+Math.round(58*drawScale),0xFFFFFFFF);
            if(selectedId!=null){
                int ax=drawLeft+Math.round(ACTION_LEFT*drawScale),ay=drawTop+Math.round(ACTION_TOP*drawScale);
                context.fill(ax,ay,ax+Math.round(ACTION_WIDTH*drawScale),ay+Math.round(ACTION_HEIGHT*drawScale),0xFFAB1853);
                boolean has=MpsqLocalWorldStore.owns(selectedId);
                context.centeredText(font,has?"Anlegen":"Kaufen",ax+Math.round(ACTION_WIDTH*drawScale/2),ay+6,0xFFFFFFFF);
                if(!has)context.centeredText(font,price(selectedItem())+" Punkte",ax+Math.round(ACTION_WIDTH*drawScale/2),ay+17,0xFFFFFFFF);
            }
        }
        drawAccessoryIcons(context);
        drawPlayerPreview(context, mouseX, mouseY);
        if (!status.isBlank()) {
            context.centeredText(font, status,
                    drawLeft + Math.round((GRID_LEFT + GRID_COLUMNS * SLOT_SIZE / 2.0f) * drawScale),
                    drawTop + Math.round(145 * drawScale), 0xFFE6E1E3);
        }
        double tx=guiX(mouseX),ty=guiY(mouseY);
        if(inside(tx,ty,GRID_LEFT,GRID_TOP,GRID_COLUMNS*SLOT_SIZE,GRID_ROWS*SLOT_SIZE)){
            JsonObject hovered=itemAt((int)(ty-GRID_TOP)/SLOT_SIZE*GRID_COLUMNS+(int)(tx-GRID_LEFT)/SLOT_SIZE);
            if(hovered!=null)MpsqDescriptionTooltips.draw(context,"accessory",accessoryId(hovered),mouseX,mouseY,width,height);
        }
    }

    private void drawAccessoryIcons(GuiGraphicsExtractor context) {
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            JsonObject row = itemAt(slot);
            if (row == null) continue;
            int column = slot % GRID_COLUMNS;
            int rowIndex = slot / GRID_COLUMNS;
            int centerX = drawLeft + Math.round((GRID_LEFT + column * SLOT_SIZE + SLOT_SIZE / 2.0f) * drawScale);
            int centerY = drawTop + Math.round((GRID_TOP + rowIndex * SLOT_SIZE + SLOT_SIZE / 2.0f) * drawScale);
            int iconSize = Math.max(12, Math.round(27 * drawScale));

            if (selectedId != null && selectedId.equals(accessoryId(row))) {
                int half = Math.max(1, Math.round(15 * drawScale));
                context.fill(centerX - half, centerY - half, centerX + half, centerY - half + 2, 0xFFFF1764);
                context.fill(centerX - half, centerY + half - 2, centerX + half, centerY + half, 0xFFFF1764);
                context.fill(centerX - half, centerY - half, centerX - half + 2, centerY + half, 0xFFFF1764);
                context.fill(centerX + half - 2, centerY - half, centerX + half, centerY + half, 0xFFFF1764);
            }

            String url = previewUrl(row);
            if (url != null && !url.isBlank()) {
                MpsqAccessoryRenderer.drawGuiPreview(context, url, centerX, centerY, iconSize / 20.0f);
            }
        }
    }

    private void drawPlayerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        int left = drawLeft + Math.round((PREVIEW_LEFT + 5) * drawScale);
        int right = drawLeft + Math.round((PREVIEW_LEFT + PREVIEW_WIDTH - 5) * drawScale);
        int top = drawTop + Math.round((PREVIEW_TOP + 5) * drawScale);
        int bottom = drawTop + Math.round((PREVIEW_TOP + PREVIEW_HEIGHT - 3) * drawScale);
        float modelSize = 25.0f * drawScale;
        String url = previewUrl(selectedItem());
        MpsqAccessoryRenderer.beginMenuPlayerPreview(url);
        try {
        InventoryScreen.extractEntityInInventoryFollowsMouse(context, left, top, right, bottom, Math.round(modelSize),
                    .15f, mouseX, mouseY, minecraft.player);
        } finally {
            MpsqAccessoryRenderer.endMenuPlayerPreview();
        }
    }

    private static String accessoryId(JsonObject row) {
        return str(row, "accessory_id", str(row, "id", ""));
    }

    private static String str(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private double guiX(double mouseX) { return (mouseX - drawLeft) / drawScale; }
    private double guiY(double mouseY) { return (mouseY - drawTop) / drawScale; }

    private static boolean inside(double x, double y, int left, int top, int boxWidth, int boxHeight) {
        return x >= left && x < left + boxWidth && y >= top && y < top + boxHeight;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || drawScale <= 0) return true;
        double x = guiX(mouseX);
        double y = guiY(mouseY);

        if (inside(x, y, 17, 218, 32, 31)) {
            if (page > 0) page--;
            else onClose();
            return true;
        }
        if (inside(x, y, 157, 216, 39, 34)) {
            minecraft.gui.setScreen(new MpsqCollectionTutorialScreen(this, "Accessoires"));
            return true;
        }
        if (page < pageCount() - 1 && inside(x, y, 307, 218, 34, 31)) {
            page++;
            return true;
        }

        if (inside(x, y, ACTION_LEFT, ACTION_TOP, ACTION_WIDTH, ACTION_HEIGHT) && !pending) {
            if(shopMode && selectedId!=null){buyOrEquip();return true;}
            if (stateIndex() == 1 && selectedId != null) setEquipped(selectedId);
            else if (stateIndex() == 2) setEquipped(null);
            return true;
        }

        if (inside(x, y, GRID_LEFT, GRID_TOP, GRID_COLUMNS * SLOT_SIZE, GRID_ROWS * SLOT_SIZE)) {
            int column = (int) (x - GRID_LEFT) / SLOT_SIZE;
            int row = (int) (y - GRID_TOP) / SLOT_SIZE;
            JsonObject item = itemAt(row * GRID_COLUMNS + column);
            if (item != null) selectedId = accessoryId(item);
            return true;
        }

        // No chest container is attached: the X-marked and inventory areas
        // are artwork only and cannot transfer or consume any item.
        return true;
    }

    private static int price(JsonObject row){return row!=null&&row.has("price_points")?Math.max(0,row.get("price_points").getAsInt()):500;}
    private void buyOrEquip(){
        if(selectedItem()!=null&&selectedItem().has("client_builtin")){setEquipped(selectedId);return;}
        if(MpsqLocalWorldStore.owns(selectedId)){setEquipped(selectedId);return;}
        if(localWorld()){
            int cost=price(selectedItem());
            if(!MpsqLocalWorldStore.spendPoints(cost)){status="Nicht genügend Punkte.";return;}
            if(!MpsqLocalWorldStore.ownAccessory(selectedId)){MpsqLocalWorldStore.addPoints(cost);status="Kauf konnte nicht gespeichert werden.";return;}
            status="Accessoire freigeschaltet.";return;
        }
        pending=true;JsonObject body=new JsonObject();body.addProperty("accessoryId",selectedId);
        MpsqApiClient.post("/me/accessories/buy",body).whenComplete((data,error)->minecraft.execute(()->{pending=false;status=error==null?"Accessoire freigeschaltet.":"Kauf fehlgeschlagen.";}));
    }

    private void setEquipped(String id) {
        if (pending) return;
        String equippedUrl = id == null ? null : previewUrl(selectedItem());
        if(!localWorld()&&((selectedItem()!=null&&selectedItem().has("client_builtin"))||id==null&&MpsqAccessoryRenderer.hasBuiltinOverride())){
            equippedId=id;if(id==null)selectedId=null;updateEquippedFlags();MpsqAccessoryRenderer.setBuiltinOverride(equippedUrl);status=id==null?"Accessoire abgelegt.":"Lokales Accessoire angelegt.";return;
        }
        if (localWorld()) {
            boolean saved = MpsqLocalWorldStore.equip(id);
            if (saved) {
                equippedId = id;
                if (id == null) selectedId = null;
                updateEquippedFlags();
                status = id == null ? "Accessoire abgelegt." : "Accessoire angelegt.";
                MpsqAccessoryRenderer.refresh();
                MpsqAccessoryRenderer.setLocalEquippedAccessory(equippedUrl);
            } else {
                status = "Accessoire konnte nicht geändert werden.";
            }
            return;
        }

        pending = true;
        JsonObject body = new JsonObject();
        if (id == null) body.add("id", JsonNull.INSTANCE);
        else body.addProperty("id", id);
        MpsqApiClient.post("/me/accessories/equip", body).whenComplete((data, error) -> minecraft.execute(() -> {
            pending = false;
            if (error == null) {
                equippedId = id;
                if (id == null) selectedId = null;
                updateEquippedFlags();
                status = id == null ? "Accessoire abgelegt." : "Accessoire angelegt.";
                MpsqAccessoryRenderer.refresh();
                MpsqAccessoryRenderer.setLocalEquippedAccessory(equippedUrl);
            } else {
                status = "Accessoire konnte nicht geändert werden.";
            }
        }));
    }

    private void updateEquippedFlags() {
        for (JsonElement element : owned) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            row.addProperty("equipped", equippedId != null && equippedId.equals(accessoryId(row)));
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
