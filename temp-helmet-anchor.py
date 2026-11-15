from pathlib import Path
b=Path('src/client/java/de/galaxushd/mpsqcamera')
p=b/'MpsqAccessoryRenderer.java';s=p.read_text(encoding='utf-8');start=s.index('            for(var player:client.level.players()){',s.index('WorldRenderEvents.AFTER_ENTITIES.register'));end=s.index('            for(var value:npcs)',start);s=s[:start]+s[end:]
a=s.index('    public static void renderMenuPlayerPreviewAccessory(');z=s.index('    private static HeadAngles interpolatedHeadAngles',a)
s=s[:a]+'''    public static void captureWornAccessory(net.minecraft.client.player.AbstractClientPlayer player,net.minecraft.client.renderer.entity.state.AvatarRenderState state){
        if(menuPreviewStates.containsKey(state)||menuPlayerPreviewActive)return;
        if(!TeamVisibilitySettings.visible()||player.isInvisible()||player.isSpectator())return;
        String url=player==Minecraft.getInstance().player&&tryOnUrl!=null?tryOnUrl:wearers.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
        if(url!=null)menuPreviewStates.put(state,url);
    }
    /** Called inside the player's model transform, just like vanilla helmet layers. */
    public static void renderAttachedHat(net.minecraft.client.renderer.entity.state.AvatarRenderState state,net.minecraft.client.model.geom.ModelPart head,PoseStack matrices,MultiBufferSource consumers){
        String url=menuPreviewStates.get(state);if(url==null)return;
        Model model=models.get(url);
        if(model==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        matrices.pushPose();
        head.translateAndRotate(matrices);
        matrices.scale(-1f,-1f,1f);
        matrices.rotate(Axis.YP.rotationDegrees(180));
        HeadDisplay display=model.headDisplay();
        if(display!=null){
            matrices.translate(display.translation()[0]/16.0,.225+display.translation()[1]/16.0,display.translation()[2]/16.0);
            matrices.rotate(Axis.ZP.rotationDegrees(display.rotation()[2]));
            matrices.rotate(Axis.YP.rotationDegrees(display.rotation()[1]));
            matrices.rotate(Axis.XP.rotationDegrees(display.rotation()[0]));
            matrices.scale(display.scale()[0]/16,display.scale()[1]/16,display.scale()[2]/16);
            matrices.translate(-8,-8,-8);
        }else{
            HeadBounds bounds=headBounds(model);float fit=Math.min(1f,10f/Math.max(bounds.width(),bounds.depth()))/16;
            matrices.translate(-bounds.centerX()*fit,.5-bounds.minY()*fit,-bounds.centerZ()*fit);matrices.scale(fit,fit,fit);
        }
        drawBbModel(model,matrices,consumers,0xFFFFFFFF);
        matrices.popPose();
    }
''' +s[z:];p.write_text(s,encoding='utf-8')
p=b/'mixin/client/PlayerEntityRendererMixin.java';s=p.read_text(encoding='utf-8-sig').replace('if (!(source instanceof AbstractClientPlayer player)) return;','if (!(source instanceof AbstractClientPlayer player)) return;\n        de.galaxushd.mpsqcamera.MpsqAccessoryRenderer.captureWornAccessory(player,state);');p.write_text(s,encoding='utf-8')
p=b/'mixin/client/LivingEntityRendererMixin.java';s=p.read_text(encoding='utf-8-sig');s=s.replace('public abstract class LivingEntityRendererMixin {','public abstract class LivingEntityRendererMixin {\n    @org.spongepowered.asm.mixin.Shadow protected net.minecraft.client.model.EntityModel<?> model;')
s=s.replace('at = @At("TAIL"))','at = @At(value="INVOKE", target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift=At.Shift.BEFORE))')
s=s.replace('if (state instanceof AvatarRenderState playerState) {','if (state instanceof AvatarRenderState playerState && model instanceof net.minecraft.client.model.player.PlayerModel playerModel) {').replace('MpsqAccessoryRenderer.renderMenuPlayerPreviewAccessory(playerState, matrices, consumers);','MpsqAccessoryRenderer.renderAttachedHat(playerState, playerModel.head, matrices, consumers);');p.write_text(s,encoding='utf-8')
# Avoid drawing base-head texels underneath fully opaque overlay texels on NPCs.
p=b/'MpsqSkinImages.java';s=p.read_text(encoding='utf-8-sig');s=s.replace('    static void normalizePixels(','''    static void removeCoveredHeadPixels(NativeImage image){
        for(int y=0;y<16;y++)for(int x=0;x<32;x++)
            if((image.getPixel(x+32,y)>>>24)==255)image.setPixel(x,y,image.getPixel(x,y)&0xFFFFFF);
    }
    static void normalizePixels(''');p.write_text(s,encoding='utf-8')
p=b/'MpsqNpcSkinRenderer.java';s=p.read_text(encoding='utf-8-sig').replace('image = MpsqSkinImages.normalize(image);','image = MpsqSkinImages.normalize(image);\n                MpsqSkinImages.removeCoveredHeadPixels(image);');p.write_text(s,encoding='utf-8')
