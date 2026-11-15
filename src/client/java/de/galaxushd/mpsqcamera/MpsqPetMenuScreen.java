package de.galaxushd.mpsqcamera;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shared inventory-style screen for the player's Mini-Me and animal pets. */
public final class MpsqPetMenuScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 264;
    private static final int GRID_LEFT = 14;
    private static final int GRID_TOP = 70;
    private static final int SLOT_SIZE = 36;
    private static final int PET_ROWS = 4;
    private static final int PET_COLUMNS = 9;
    private static final int ACTION_MAX_WIDTH = SLOT_SIZE * 2;
    private static final int ACTION_TEXTURE_WIDTH = 1984;
    private static final int ACTION_TEXTURE_HEIGHT = 352;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            MpsqCameraClient.MOD_ID, "textures/gui/mpsq_pets_menu.png");
    private static final Identifier EQUIP_ACTION = Identifier.fromNamespaceAndPath(
            MpsqCameraClient.MOD_ID, "textures/gui/pet_equip.png");
    private static final Identifier EQUIPPED_ACTION = Identifier.fromNamespaceAndPath(
            MpsqCameraClient.MOD_ID, "textures/gui/pet_equipped.png");

    public enum PetKind { MINI_ME, ANIMALS }

    private final Screen parent;
    private final PetKind kind;
    private String selectedId;
    private boolean variantsOpen;
    private static final List<String> BUDGIES=List.of("green","blue_spangle","cobalt","gray","green_spangle","light_green","olive","sky_blue","white","yellow");
    private static final List<String> OWLS=List.of("barn","horned","long_eared","snowy");
    private float drawScale;
    private int drawLeft;
    private int drawTop;
    private PetPreviewPlayer previewPlayer;

    public MpsqPetMenuScreen(Screen parent) {
        this(parent, PetKind.MINI_ME);
    }

    public MpsqPetMenuScreen(Screen parent, PetKind kind) {
        super(Component.literal(kind == PetKind.MINI_ME ? "Pets · Mini-Me" : "Pets · Tiere & Co."));
        this.parent = parent;
        this.kind = kind;
        // Opening the menu does not open the action tag automatically.
        this.selectedId = null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        drawScale = Math.min(1.0f, Math.min((width - 16.0f) / ART_WIDTH, (height - 16.0f) / ART_HEIGHT));
        drawScale = Math.max(0.1f, drawScale);
        int drawWidth = Math.round(ART_WIDTH * drawScale);
        int drawHeight = Math.round(ART_HEIGHT * drawScale);
        drawLeft = (width - drawWidth) / 2;
        drawTop = (height - drawHeight) / 2;

        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                drawLeft, drawTop, 0, 0, drawWidth, drawHeight,
                ART_WIDTH, ART_HEIGHT, ART_WIDTH, 442);

        if (kind == PetKind.MINI_ME) {
            drawMiniMePets(context);
        } else {
            drawAnimalPets(context);
        }
        drawSelectedPetAction(context);
        drawVariants(context);
        double tx=(mouseX-drawLeft)/drawScale,ty=(mouseY-drawTop)/drawScale;
        if(inside(tx,ty,GRID_LEFT,GRID_TOP,PET_COLUMNS*SLOT_SIZE,PET_ROWS*SLOT_SIZE)){
            int index=(int)(ty-GRID_TOP)/SLOT_SIZE*PET_COLUMNS+(int)(tx-GRID_LEFT)/SLOT_SIZE;
            var pets=kind==PetKind.MINI_ME?miniYouEntries():animalEntries();
            if(index<pets.size())MpsqDescriptionTooltips.draw(context,"pet",pets.get(index).id(),mouseX,mouseY,width,height);
        }
    }

    private void drawMiniMePets(GuiGraphicsExtractor context) {
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

    private void drawAnimalPets(GuiGraphicsExtractor context) {
        List<MpsqPetCatalog.Pet> pets = animalEntries();
        for (int index = 0; index < pets.size() && index < PET_ROWS * PET_COLUMNS; index++) {
            MpsqPetCatalog.Pet pet = pets.get(index);
            int column = index % PET_COLUMNS, row = index / PET_COLUMNS;
            int x = drawLeft + Math.round((GRID_LEFT + column * SLOT_SIZE) * drawScale);
            int y = drawTop + Math.round((GRID_TOP + row * SLOT_SIZE) * drawScale);
            drawPetModel(context,pet,x,y);
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

    private void drawPetModel(GuiGraphicsExtractor context, MpsqPetCatalog.Pet pet, int slotX, int slotY) {drawPetModel(context,pet,slotX,slotY,drawScale);}
    private void drawPetModel(GuiGraphicsExtractor context, MpsqPetCatalog.Pet pet, int slotX, int slotY,float drawScale) {
        if (minecraft == null) return;
        MpsqNpcSkinRenderer.Skin skin = MpsqPetRenderer.skinFor(pet);
        if (skin == null && pet.miniModel()!=null) return;

        if (minecraft.level == null) return;
        if (previewPlayer == null || previewPlayer.level() != minecraft.level) {
            previewPlayer = new PetPreviewPlayer(minecraft.level);
        }
        if(skin!=null)previewPlayer.setPreviewSkin(skin);
        int left = slotX + Math.round(3 * drawScale);
        int top = slotY + Math.round(2 * drawScale);
        int right = slotX + Math.round((SLOT_SIZE - 3) * drawScale);
        int bottom = slotY + Math.round((SLOT_SIZE - 2) * drawScale);
        int size = Math.max(4, Math.round(12 * drawScale));
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;

        // InventoryScreen manages the GUI's 3D target and MatrixStack. The
        // custom model needs its own vertical centre rather than the player bounds.
        // Centres of the scaled preview geometry, including tail/outer layers.
        float modelCentre = switch (pet.id()) {
            case "nogs_budgie" -> 0.28f;
            case "nocsy_otter" -> 0.76f;
            case "nogs_hedgehog" -> 0.70f;
            default -> 0.84f;
        };
        float verticalOffset = modelCentre - previewPlayer.getBbHeight() * 0.5f;
        MpsqPetPreview.extract(pet.id(), () -> InventoryScreen.extractEntityInInventoryFollowsMouse(context, left, top, right, bottom,
                size, verticalOffset, centerX, centerY, previewPlayer));
    }

    private void drawSelectedPetAction(GuiGraphicsExtractor context) {
        if (selectedId == null) return;
        List<MpsqPetCatalog.Pet> pets = kind == PetKind.MINI_ME
                ? miniYouEntries() : animalEntries();
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
        context.blit(RenderPipelines.GUI_TEXTURED, texture, screenX, screenY,
                0, 0, screenWidth, screenHeight,
                ACTION_TEXTURE_WIDTH, ACTION_TEXTURE_HEIGHT,
                ACTION_TEXTURE_WIDTH, ACTION_TEXTURE_HEIGHT);
    }

    private boolean clickSelectedPetAction(double guiX, double guiY) {
        if (selectedId == null) return false;
        List<MpsqPetCatalog.Pet> pets = kind == PetKind.MINI_ME
                ? miniYouEntries() : animalEntries();
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

    private static List<MpsqPetCatalog.Pet> animalEntries(){
        return java.util.stream.Stream.of("nogs_budgie","nocsy_otter","nogs_hedgehog",
                MpsqPetSelectionStore.owlVariant(),"mpsq_baby_happy_ghast","mpsq_zekk_the_zombie",
                "mpsq_slak_the_skeleton","mpsq_colin_the_creeper","mpsq_exo_the_enderman",
                "mpsq_crab","mpsq_penguin",MpsqPetSelectionStore.tigerVariant()).map(MpsqPetCatalog::byId).toList();
    }
    private boolean hasVariants(){return kind==PetKind.ANIMALS&&selectedId!=null&&(selectedId.equals("nogs_budgie")||selectedId.startsWith("nogs_owl_")||MpsqPetSelectionStore.isTiger(selectedId));}
    /** Coordinates in the original menu pixels; each variant slot is 18 rather than 36 pixels. */
    private int[] variantLayout(){
        var pets=animalEntries();int index=0;for(int i=0;i<pets.size();i++)if(pets.get(i).id().equals(selectedId))index=i;
        int center=GRID_LEFT+(index%PET_COLUMNS)*SLOT_SIZE+SLOT_SIZE/2;
        int buttonX=Math.clamp(center-36,0,ART_WIDTH-72),y=GRID_TOP+(index/PET_COLUMNS+1)*SLOT_SIZE+14;
        int barWidth=selectedId.equals("nogs_budgie")?194:MpsqPetSelectionStore.isTiger(selectedId)?50:86;
        int barX=selectedId.equals("nogs_budgie")?0:Math.clamp(center-barWidth/2,0,ART_WIDTH-barWidth);
        return new int[]{buttonX,y,barX,y+15,barWidth};
    }
    private void drawVariants(GuiGraphicsExtractor g){
        if(!hasVariants())return;var b=variantLayout();
        g.pose().pushMatrix();g.pose().translate(drawLeft,drawTop);g.pose().scale(drawScale);
        g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"textures/gui/pet_variant_button.png"),b[0],b[1],0,0,72,13,1968,352,1968,352);
        if(variantsOpen){
            boolean budgie=selectedId.equals("nogs_budgie");boolean tiger=MpsqPetSelectionStore.isTiger(selectedId);var variants=budgie?BUDGIES:tiger?List.of("mpsq_tiger","mpsq_tiger_baby"):OWLS;
            var texture=Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"textures/gui/pet_variants_"+(budgie?"10":tiger?"2":"4")+".png");
            g.blit(RenderPipelines.GUI_TEXTURED,texture,b[2],b[3],0,0,b[4],32,b[4]*2,64,b[4]*2,64);
            String current=budgie?MpsqPetSelectionStore.budgieVariant():tiger?MpsqPetSelectionStore.tigerVariant():MpsqPetSelectionStore.owlVariant().substring("nogs_owl_".length());
            for(int i=0;i<variants.size();i++){
                int x=b[2]+7+i*18,y=b[3]+7;
                if(variants.get(i).equals(current)){g.fill(x,y,x+17,y+17,0xFFFFC34A);g.fill(x+1,y+1,x+16,y+16,0xFF707070);}
                if(!tiger)g.item(MpsqCinematicNpcScreen.stack("mpsqcamera:nm_head_"+(budgie?"budgie_":"owl_")+variants.get(i)),x+1,y+1);
            }
        }g.pose().popMatrix();
        if(variantsOpen&&MpsqPetSelectionStore.isTiger(selectedId)){for(int i=0;i<2;i++)drawPetModel(g,MpsqPetCatalog.byId(i==0?"mpsq_tiger":"mpsq_tiger_baby"),drawLeft+Math.round((b[2]+7+i*18)*drawScale),drawTop+Math.round((b[3]+7)*drawScale),drawScale*.5f);}
    }
    private boolean clickVariants(double x,double y){
        if(!hasVariants())return false;var b=variantLayout();
        if(inside(x,y,b[0],b[1],72,13)){variantsOpen=!variantsOpen;return true;}
        if(!variantsOpen||!inside(x,y,b[2],b[3],b[4],32))return false;
        boolean budgie=selectedId.equals("nogs_budgie");boolean tiger=MpsqPetSelectionStore.isTiger(selectedId);var variants=budgie?BUDGIES:tiger?List.of("mpsq_tiger","mpsq_tiger_baby"):OWLS;
        if(y>=b[3]+7&&y<b[3]+25){int i=(int)Math.floor((x-b[2]-7)/18);if(i>=0&&i<variants.size()){
            if(budgie)MpsqPetSelectionStore.selectBudgieVariant(variants.get(i));
            else if(tiger){if(MpsqPetSelectionStore.selectTigerVariant(variants.get(i)))selectedId=variants.get(i);}
            else if(MpsqPetSelectionStore.selectOwlVariant("nogs_owl_"+variants.get(i)))selectedId="nogs_owl_"+variants.get(i);
        }}return true;
    }

    private static final class PetPreviewPlayer extends RemotePlayer {
        private PlayerSkin previewSkin;

        private PetPreviewPlayer(ClientLevel world) {
            super(world, new GameProfile(UUID.randomUUID(), "MPSQ Pet Preview"));
            setId(-2000000000); // Render-only preview; never added to the world.
        }

        private void setPreviewSkin(MpsqNpcSkinRenderer.Skin skin) {
            previewSkin = new PlayerSkin(new net.minecraft.core.ClientAsset.ResourceTexture(skin.texture(), skin.texture()), null, null,
                    skin.slim() ? net.minecraft.world.entity.player.PlayerModelType.SLIM : net.minecraft.world.entity.player.PlayerModelType.WIDE, false);
        }

        @Override
        public PlayerSkin getSkin() {
            return previewSkin == null ? super.getSkin() : previewSkin;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if ((button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) || drawScale <= 0) return true;

        double guiX = (mouseX - drawLeft) / drawScale;
        double guiY = (mouseY - drawTop) / drawScale;

        // The arrows and wiki icon occupy the bottom row of the artwork.
        if (inside(guiX, guiY, 17, 216, 32, 33)) {
            onClose();
            return true;
        }
        if (inside(guiX, guiY, 157, 212, 38, 39)) {
            minecraft.gui.setScreen(new MpsqCollectionTutorialScreen(this, "Pets"));
            return true;
        }
        if (inside(guiX, guiY, 307, 216, 34, 33)) {
            PetKind other = kind == PetKind.MINI_ME ? PetKind.ANIMALS : PetKind.MINI_ME;
            minecraft.gui.setScreen(new MpsqPetMenuScreen(parent, other));
            return true;
        }

        if(button==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && clickVariants(guiX,guiY))return true;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && clickSelectedPetAction(guiX, guiY)) return true;

        if (inside(guiX, guiY, GRID_LEFT, GRID_TOP,
                PET_COLUMNS * SLOT_SIZE, PET_ROWS * SLOT_SIZE)) {
            int column = (int) (guiX - GRID_LEFT) / SLOT_SIZE;
            int row = (int) (guiY - GRID_TOP) / SLOT_SIZE;
            int index = row * PET_COLUMNS + column;
            List<MpsqPetCatalog.Pet> pets = kind == PetKind.MINI_ME
                    ? miniYouEntries() : animalEntries();
            if (index >= 0 && index < pets.size()) {
                MpsqPetCatalog.Pet pet = pets.get(index);
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && pet.id().equals("nogs_budgie")) {
                    selectedId=pet.id();variantsOpen=!variantsOpen;
                    return true;
                }
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                    if (canEquip(pet)) {
                        selectedId = pet.id().equals(selectedId) ? null : pet.id();variantsOpen=false;
                    }
                }
            }
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !inside(guiX, guiY, GRID_LEFT, GRID_TOP,
                PET_COLUMNS * SLOT_SIZE, PET_ROWS * SLOT_SIZE)) selectedId = null;

        // There is no container inventory: empty slots and decorative X areas
        // cannot accept, remove, or transfer items and never get hover effects.
        return true;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
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
