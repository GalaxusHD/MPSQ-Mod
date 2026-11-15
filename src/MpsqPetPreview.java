package de.galaxushd.mpsqcamera;
import java.util.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
public final class MpsqPetPreview {
    private static String extracting;
    private static final long CLOCK_START = System.nanoTime();
    private record Preview(String id,MpsqNpcSkinRenderer.Skin skin){}
    private static final Map<LivingEntityRenderState,Preview> STATES=Collections.synchronizedMap(new WeakHashMap<>());
    static void extract(String id,Runnable action){String old=extracting;extracting=id;try{action.run();}finally{extracting=old;}}
    public static void capture(LivingEntityRenderState state){
        STATES.remove(state);
        if(extracting!=null){var pet=MpsqPetCatalog.byId(extracting);STATES.put(state,new Preview(extracting,pet.miniModel()==null?null:MpsqPetRenderer.skinFor(pet)));}
    }
    public static boolean render(LivingEntityRenderState state,PoseStack matrices,MultiBufferSource buffers){
        Preview p=STATES.get(state);if(p==null)return false;
        double time=(System.nanoTime()-CLOCK_START)/1e9;
        if(MpsqCustomPetRenderer.isCustomPet(p.id)){
            MpsqAccessoryRenderer.renderPetPreview(p.id,matrices,buffers,(float)time);
            buffers.endBatch();return true;
        }
        switch(p.id){
            case "nogs_budgie" -> MpsqBudgieModel.render(matrices,buffers,0xF000F0,0,0,0,155,2.1f,MpsqPetSelectionStore.budgieVariant(),"idle",time);
            case "nocsy_otter" -> MpsqOtterModel.render(matrices,buffers,0xF000F0,0,0,0,155,1.75f,"idle",time);
            case "nogs_owl_barn","nogs_owl_horned","nogs_owl_long_eared","nogs_owl_snowy" -> {MpsqOwlModel.select(p.id);MpsqOwlModel.render(matrices,buffers,0xF000F0,0,0,0,155,2f,"idle",time);}
            case "nogs_hedgehog" -> MpsqHedgehogModel.render(matrices,buffers,0xF000F0,0,0,0,155,3f,"idle",time);
            default -> MpsqMiniYouModel.render(matrices,buffers,0xF000F0,p.id,p.skin==null?null:p.skin.texture(),p.skin!=null&&p.skin.slim(),0,0,0,155,1.4f,"idle",time);
        }
        buffers.endBatch();return true;
    }
}
