package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Product selection before creating a placement; cancelling never leaves a ghost NPC. */
final class MpsqScenesProducts extends Screen {
    private final Screen parent; private int page;private String status="";
    private final net.minecraft.client.multiplayer.ClientLevel world=net.minecraft.client.Minecraft.getInstance().level;
    private Product pending;private long clicked;
    private record Product(String name,String type,String category,String url){}
    MpsqScenesProducts(Screen parent){super(Component.literal("Scenes · NPC-Produkte"));this.parent=parent;}
    @Override protected void init(){
        var list=new ArrayList<Product>();list.add(new Product("Eigener Skin","self","npc_skin_normal","self"));var seen=new HashSet<String>();
        for(var e:MpsqBuiltinNpcCatalog.catalog()){var o=e.getAsJsonObject();String category=MpsqScenes.str(o,"category","");String id=MpsqScenes.str(o,"id","");if((category.startsWith("npc_skin")||category.equals("npc_model"))&&seen.add(id))list.add(new Product(MpsqScenes.str(o,"display_name",id),id,category,MpsqScenes.str(o,"url","")));}
        if(seen.add(MpsqAccessoryRenderer.WUMPUS_ASSET_ID))list.add(new Product("Wumpus",MpsqAccessoryRenderer.WUMPUS_ASSET_ID,"npc_model",MpsqAccessoryRenderer.assetPreviewUrl(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,"")));
        list.add(new Product("Dorfbewohner","minecraft:villager","mob",""));
        page=Math.min(page,Math.max(0,(list.size()-1)/10));for(int i=page*10;i<Math.min(list.size(),page*10+10);i++){var p=list.get(i);int n=i-page*10;addRenderableWidget(new MpsqScenesWidget(width/2-155+n%2*157,45+n/2*27,153,24,p.name,null,()->select(p)));}
        addRenderableWidget(new MpsqScenesWidget(width/2-155,height-58,153,23,"←",null,()->{page=Math.max(0,page-1);rebuildWidgets();}));addRenderableWidget(new MpsqScenesWidget(width/2+2,height-58,153,23,"→",null,()->{page++;rebuildWidgets();}));addRenderableWidget(new MpsqScenesWidget(width/2-75,height-30,150,23,"Zurück",null,this::onClose));
    }
    private void select(Product p){if(p.equals(pending)&&System.nanoTime()-clicked<350_000_000L){pending=null;var template=new MpsqScenes.Profile();template.npc.name=p.name;template.npc.type=p.type;template.npc.category=p.category;template.npc.url=p.url;MpsqScenesTool.beginPlace(template,parent);}else{pending=p;clicked=System.nanoTime();status="Ein Klick: erstellen · Doppelklick: Platzierwerkzeug";}}
    private void spawn(Product product){try{if(minecraft.level!=world||!MpsqScenes.allowed())throw new IllegalStateException("Welt oder Bearbeitungsrechte geändert.");var t=MpsqScenes.create();var p=t.profile();p.npc.name=product.name;p.npc.type=product.type;p.npc.category=product.category;p.npc.url=product.url;MpsqScenes.save(t,p);minecraft.gui.setScreen(new MpsqScenesScreen(t,parent,null));}catch(Exception e){status=e.getMessage();}}
    @Override public void tick(){if(minecraft.level!=world){pending=null;minecraft.gui.setScreen(null);}else if(pending!=null&&System.nanoTime()-clicked>=350_000_000L){var p=pending;pending=null;spawn(p);}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,18,0xFFFFFFFF);g.centeredText(font,status,width/2,height-73,0xFFFFFFFF);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
