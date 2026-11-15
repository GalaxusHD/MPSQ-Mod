package de.galaxushd.mpsqcamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
/** Item IDs or MPSQ PNG resource IDs; items retain Minecraft's 3D GUI renderer. */
public final class MpsqGuiIcon {
    public static Component itemName(String value){try{return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(value))).getHoverName();}catch(Exception e){return Component.literal(value);}}
    public static void draw(GuiGraphicsExtractor g,String value,int x,int y,int size){
        try{
            Identifier id=Identifier.parse(value);var item=BuiltInRegistries.ITEM.getValue(id);
            if(item!=Items.AIR){g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/16f);g.item(new ItemStack(item),0,0);g.pose().popMatrix();return;}
            if(id.getNamespace().equals("mpsq")||id.getNamespace().equals("mpsqcamera")){
                String path=id.getPath();if(!path.startsWith("textures/"))path="textures/gui/"+path;if(!path.endsWith(".png"))path+=".png";
                var texture=Identifier.fromNamespaceAndPath("mpsqcamera",path);
                if(Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()){g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,0,0,size,size,size,size,size,size);return;}
            }
            g.text(Minecraft.getInstance().font,"?",x+size/2-3,y+size/2-4,0xFFFFFFFF);
        }catch(Exception ignored){}
    }
}
