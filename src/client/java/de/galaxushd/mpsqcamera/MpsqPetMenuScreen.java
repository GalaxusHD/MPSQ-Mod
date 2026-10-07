package de.galaxushd.mpsqcamera;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

/** Shared inventory-style screen for the player's Mini-Me and animal pets. */
public final class MpsqPetMenuScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 250;
    private static final int GRID_LEFT = 14;
    private static final int GRID_TOP = 70;
    private static final int SLOT_SIZE = 36;
    private static final int PET_ROWS = 4;
    private static final int PET_COLUMNS = 9;
    private static final int ACTION_MAX_WIDTH = SLOT_SIZE * 2;
    private static final int ACTION_TEXTURE_WIDTH = 1984;
    private static final int ACTION_TEXTURE_HEIGHT = 352;
    private static final Identifier BACKGROUND = Identifier.of(
            MpsqCameraClient.MOD_ID, "textures/gui/mpsq_pets_menu.png");
    private static final Identifier EQUIP_ACTION = Identifier.of(
            MpsqCameraClient.MOD_ID, "textures/gui/pet_equip.png");
    private static final Identifier EQUIPPED_ACTION = Identifier.of(
            MpsqCameraClient.MOD_ID, "textures/gui/pet_equipped.png");

    public enum PetKind { MINI_ME, ANIMALS }

    private final Screen parent;
    private final PetKind kind;
    private String selectedId;
    private float drawScale;
    private int drawLeft;
    private int drawTop;
    private PetPreviewPlayer previewPlayer;

    public MpsqPetMenuScreen(Screen parent) {
        this(parent, PetKind.MINI_ME);
    }

    public MpsqPetMenuScreen(Screen parent, PetKind kind) {
        super(Text.literal(kind == PetKind.MINI_ME ? "Pets · Mini-Me" : "Pets · Tiere & Co."));
        this.parent = parent;
        this.kind = kind;
        // Opening the menu does not open the action tag automatically.
        this.selectedId = null;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        drawScale = Math.min(1.0f, Math.min((width - 16.0f) / ART_WIDTH, (height - 16.0f) / ART_HEIGHT));
        drawScale = Math.max(0.1f, drawScale);
        int drawWidth = Math.round(ART_WIDTH * drawScale);
        int drawHeight = Math.round(ART_HEIGHT * drawScale);
        drawLeft = (width - drawWidth) / 2;
        drawTop = (height - drawHeight) / 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                drawLeft, drawTop, 0, 0, drawWidth, drawHeight,
                ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);

        if (kind == PetKind.MINI_ME) {
            drawMiniMePets(context);
        } else {
            drawAnimalPets(context);
        }
        drawSelectedPetAction(context);
    }

    private void drawMiniMePets(DrawContext context) {
        List<MpsqPetCatalog.Pet> pets = miniYouEntries();
        for (int index = 0; index < pets.size() && index < PET_ROWS * PET_COLUMNS; index++) {
            MpsqPetCatalog.Pet pet = pets.get(index);
            int column = index % PET_COLUMNS;
            int row = index / PET_COLUMNS;
            int x = drawLeft + Math.round((GRID_LEFT + column * SLOT_SIZE) * drawScale);
            int y = drawTop + Math.round((GRID_TOP + row * SLOT_SIZE) * drawScale);

            // InventoryScreen uses Minecraft's real 3D player renderer. The pet
            // model context applies the Mini-Me proportions to each fixed skin.
            drawPetModel(context, pet, x, y);
        }
    }

    private void drawAnimalPets(DrawContext context) {
        List<MpsqPetCatalog.Pet> pets = MpsqPetCatalog.group(MpsqPetCatalog.Group.ANIMAL);
        for (int index = 0; index < pets.size() && index < PET_ROWS * PET_COLUMNS; index++) {
            MpsqPetCatalog.Pet pet = pets.get(index);
            int column = index % PET_COLUMNS, row = index / PET_COLUMNS;
            int x = drawLeft + Math.round((GRID_LEFT + column * SLOT_SIZE) * drawScale);
            int y = drawTop + Math.round((GRID_TOP + row * SLOT_SIZE) * drawScale);
            int textureSize = pet.textureHeight();
            int size = Math.max(1, Math.round(26 * drawScale));
            int iconX = x + Math.round((SLOT_SIZE - 26) * drawScale / 2);
            int iconY = y + Math.round((SLOT_SIZE - 26) * drawScale / 2);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, pet.textureId(), iconX, iconY,
                    0, 0, size, size, textureSize, textureSize, textureSize, textureSize);
        }
    }

    private static List<MpsqPetCatalog.Pet> miniYouEntries() {
        return MpsqPetCatalog.group(MpsqPetCatalog.Group.MINI_YOU).stream()
                .filter(pet -> pet.issuerRole() == null || hasOfficerPetAccess()).toList();
    }

    private static boolean hasOfficerPetAccess() {
        return TeamStateStore.self().map(profile ->
                profile.permissionRank().level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    private static boolean canEquip(MpsqPetCatalog.Pet pet) {
        return pet.issuerRole() == null || hasOfficerPetAccess();
    }

    private void drawPetModel(DrawContext context, MpsqPetCatalog.Pet pet, int slotX, int slotY) {
        if (client == null) return;
        MpsqNpcSkinRenderer.Skin skin = MpsqPetRenderer.skinFor(pet);
        if (skin == null) return;

        if (client.world == null) return;
        if (previewPlayer == null || previewPlayer.getWorld() != client.world) {
            previewPlayer = new PetPreviewPlayer(client.world);
        }
        previewPlayer.setPreviewSkin(skin);
        int left = slotX + Math.round(3 * drawScale);
        int top = slotY + Math.round(2 * drawScale);
        int right = slotX + Math.round((SLOT_SIZE - 3) * drawScale);
        int bottom = slotY + Math.round((SLOT_SIZE - 2) * drawScale);
        int size = Math.max(4, Math.round(12 * drawScale));
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;

        // InventoryScreen manages the GUI's 3D target and MatrixStack. The
        // preview entity supplies the preset texture and slim/wide model.
        MpsqPetModelContext.render(() -> InventoryScreen.drawEntity(context, left, top, right, bottom,
                size, 1.0f, centerX, centerY, previewPlayer));
    }

    private void drawSelectedPetAction(DrawContext context) {
        if (selectedId == null) return;
        List<MpsqPetCatalog.Pet> pets = kind == PetKind.MINI_ME
                ? miniYouEntries() : MpsqPetCatalog.group(MpsqPetCatalog.Group.ANIMAL);
        int index = -1;
        for (int i = 0; i < pets.size(); i++) {
            if (pets.get(i).id().equals(selectedId)) { index = i; break; }
        }
        if (index < 0) return;
        MpsqPetCatalog.Pet pet = pets.get(index);
        if (!canEquip(pet)) return;
        int column = index % PET_COLUMNS, row = index / PET_COLUMNS;
        int slotX = GRID_LEFT + column * SLOT_SIZE;
        int slotY = GRID_TOP + row * SLOT_SIZE;
        int buttonWidth = Math.min(ACTION_MAX_WIDTH, ART_WIDTH);
        int buttonHeight = Math.max(1, Math.round(buttonWidth * (float) ACTION_TEXTURE_HEIGHT / ACTION_TEXTURE_WIDTH));
        int buttonX = Math.max(0, Math.min(ART_WIDTH - buttonWidth,
                slotX + SLOT_SIZE / 2 - buttonWidth / 2));
        int buttonY = slotY + SLOT_SIZE;
        int screenX = drawLeft + Math.round(buttonX * drawScale);
        int screenY = drawTop + Math.round(buttonY * drawScale);
        int screenWidth = Math.max(1, Math.round(buttonWidth * drawScale));
        int screenHeight = Math.max(1, Math.round(buttonHeight * drawScale));
        Identifier texture = selectedId.equals(MpsqPetSelectionStore.selectedId())
                ? EQUIPPED_ACTION : EQUIP_ACTION;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, screenX, screenY,
                0, 0, screenWidth, screenHeight,
                ACTION_TEXTURE_WIDTH, ACTION_TEXTURE_HEIGHT,
                ACTION_TEXTURE_WIDTH, ACTION_TEXTURE_HEIGHT);
    }

    private boolean clickSelectedPetAction(double guiX, double guiY) {
        if (selectedId == null) return false;
        List<MpsqPetCatalog.Pet> pets = kind == PetKind.MINI_ME
                ? miniYouEntries() : MpsqPetCatalog.group(MpsqPetCatalog.Group.ANIMAL);
        int index = -1;
        for (int i = 0; i < pets.size(); i++) {
            if (pets.get(i).id().equals(selectedId)) { index = i; break; }
        }
        if (index < 0 || !canEquip(pets.get(index))) return false;
        int column = index % PET_COLUMNS, row = index / PET_COLUMNS;
        int slotX = GRID_LEFT + column * SLOT_SIZE;
        int slotY = GRID_TOP + row * SLOT_SIZE;
        int buttonWidth = Math.min(ACTION_MAX_WIDTH, ART_WIDTH);
        int buttonHeight = Math.max(1, Math.round(buttonWidth * (float) ACTION_TEXTURE_HEIGHT / ACTION_TEXTURE_WIDTH));
        int buttonX = Math.max(0, Math.min(ART_WIDTH - buttonWidth,
                slotX + SLOT_SIZE / 2 - buttonWidth / 2));
        int buttonY = slotY + SLOT_SIZE;
        if (!inside(guiX, guiY, buttonX, buttonY, buttonWidth, buttonHeight)) return false;
        String newId = selectedId.equals(MpsqPetSelectionStore.selectedId()) ? null : selectedId;
        MpsqPetSelectionStore.select(newId);
        return true;
    }

    private static final class PetPreviewPlayer extends OtherClientPlayerEntity {
        private SkinTextures previewSkin;

        private PetPreviewPlayer(ClientWorld world) {
            super(world, new GameProfile(UUID.randomUUID(), "MPSQ Pet Preview"));
        }

        private void setPreviewSkin(MpsqNpcSkinRenderer.Skin skin) {
            previewSkin = new SkinTextures(skin.texture(), "", null, null,
                    skin.slim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, false);
        }

        @Override
        public SkinTextures getSkinTextures() {
            return previewSkin == null ? super.getSkinTextures() : previewSkin;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button != 0 && button != 1) || drawScale <= 0) return true;

        double guiX = (mouseX - drawLeft) / drawScale;
        double guiY = (mouseY - drawTop) / drawScale;

        // The arrows and wiki icon occupy the bottom row of the artwork.
        if (inside(guiX, guiY, 17, 216, 32, 33)) {
            close();
            return true;
        }
        if (inside(guiX, guiY, 157, 212, 38, 39)) {
            client.setScreen(new MpsqCollectionTutorialScreen(this, "Pets"));
            return true;
        }
        if (inside(guiX, guiY, 307, 216, 34, 33)) {
            PetKind other = kind == PetKind.MINI_ME ? PetKind.ANIMALS : PetKind.MINI_ME;
            client.setScreen(new MpsqPetMenuScreen(parent, other));
            return true;
        }

        if (button == 0 && clickSelectedPetAction(guiX, guiY)) return true;

        if (inside(guiX, guiY, GRID_LEFT, GRID_TOP,
                PET_COLUMNS * SLOT_SIZE, PET_ROWS * SLOT_SIZE)) {
            int column = (int) (guiX - GRID_LEFT) / SLOT_SIZE;
            int row = (int) (guiY - GRID_TOP) / SLOT_SIZE;
            int index = row * PET_COLUMNS + column;
            List<MpsqPetCatalog.Pet> pets = kind == PetKind.MINI_ME
                    ? miniYouEntries() : MpsqPetCatalog.group(MpsqPetCatalog.Group.ANIMAL);
            if (index >= 0 && index < pets.size()) {
                MpsqPetCatalog.Pet pet = pets.get(index);
                if (button == 1 && pet.id().equals("nogs_budgie")) {
                    client.setScreen(new MpsqBudgieSkinScreen(this));
                    return true;
                }
                if (button == 0) {
                    if (canEquip(pet)) {
                        selectedId = pet.id().equals(selectedId) ? null : pet.id();
                    }
                }
            }
        }

        if (button == 0 && !inside(guiX, guiY, GRID_LEFT, GRID_TOP,
                PET_COLUMNS * SLOT_SIZE, PET_ROWS * SLOT_SIZE)) selectedId = null;

        // There is no container inventory: empty slots and decorative X areas
        // cannot accept, remove, or transfer items and never get hover effects.
        return true;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
