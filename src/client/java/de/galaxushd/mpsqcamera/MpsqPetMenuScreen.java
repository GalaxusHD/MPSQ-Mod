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
    private static final Identifier BACKGROUND = Identifier.of(
            MpsqCameraClient.MOD_ID, "textures/gui/mpsq_pets_menu.png");

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
        this.selectedId = MpsqPetSelectionStore.selectedId();
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
            context.drawCenteredTextWithShadow(textRenderer, "Tier-Pets kommen bald",
                    drawLeft + Math.round(ART_WIDTH * drawScale / 2),
                    drawTop + Math.round(145 * drawScale), 0xFFE6E1E3);
        }
    }

    private void drawMiniMePets(DrawContext context) {
        List<MpsqPetCatalog.Pet> pets = MpsqPetCatalog.all();
        for (int index = 0; index < pets.size() && index < PET_ROWS * PET_COLUMNS; index++) {
            MpsqPetCatalog.Pet pet = pets.get(index);
            int column = index % PET_COLUMNS;
            int row = index / PET_COLUMNS;
            int x = drawLeft + Math.round((GRID_LEFT + column * SLOT_SIZE) * drawScale);
            int y = drawTop + Math.round((GRID_TOP + row * SLOT_SIZE) * drawScale);

            drawPetModel(context, pet, x, y);
        }
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
        int size = Math.max(4, Math.round(14 * drawScale));
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;

        // InventoryScreen manages the GUI's 3D target and MatrixStack. The
        // preview entity supplies the preset texture and slim/wide model.
        MpsqPetModelContext.render(() -> InventoryScreen.drawEntity(context, left, top, right, bottom,
                size, 1.0f, centerX, centerY, previewPlayer));
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
        if (button != 0 || drawScale <= 0) return true;

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

        if (kind == PetKind.MINI_ME && inside(guiX, guiY, GRID_LEFT, GRID_TOP,
                PET_COLUMNS * SLOT_SIZE, PET_ROWS * SLOT_SIZE)) {
            int column = (int) (guiX - GRID_LEFT) / SLOT_SIZE;
            int row = (int) (guiY - GRID_TOP) / SLOT_SIZE;
            int index = row * PET_COLUMNS + column;
            List<MpsqPetCatalog.Pet> pets = MpsqPetCatalog.all();
            if (index >= 0 && index < pets.size()) {
                MpsqPetCatalog.Pet pet = pets.get(index);
                if (pet.issuerRole() == null && MpsqPetSelectionStore.select(pet.id())) {
                    selectedId = pet.id();
                }
            }
        }

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
