package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;

/** Uses only the temporary game-test world's quest store. */
final class QuestGuiSmoke {
    static Object get(Object o,String field){try{var f=o.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    static void click(Screen s,int x,int y,int button){float scale=(float)get(s,"drawScale");int left=(int)get(s,"drawLeft"),top=(int)get(s,"drawTop");s.mouseClicked(new MouseButtonEvent(left+x*scale,top+y*scale,new MouseButtonInfo(button,0)),false);}
    static void button(Screen s,String text){for(var w:s.children())if(w instanceof MpsqScenesWidget b&&b.getMessage().getString().equals(text)){b.press();return;}else if(w instanceof Button b&&b.getMessage().getString().equals(text)){b.onPress(null);return;}throw new AssertionError("Button missing: "+text);}
    static JsonObject quest(int i){var q=new JsonObject();q.addProperty("id","quest-test-"+i);q.addProperty("npc_id","quest-test-npc");q.addProperty("title","Quest "+i);q.addProperty("description","Sammle Steine");q.addProperty("category","standard");q.addProperty("objective_item","minecraft:stone");q.addProperty("icon_item","minecraft:paper");q.addProperty("target_count",10);q.addProperty("reward_points",100);q.addProperty("accepted",false);q.addProperty("claimed",false);q.addProperty("progress",0);return q;}
    static void run(ClientGameTestContext ctx){
        ctx.runOnClient(c->{var a=new JsonArray();for(int i=0;i<5;i++)a.add(quest(i));if(!MpsqLocalWorldStore.setArray("quests",a))throw new AssertionError("Fixture save");});
        ctx.setScreen(()->new MpsqQuestMenuScreen(null,"quest-test-npc"));ctx.waitTicks(4);ctx.takeScreenshot("quest-board-border-brush");
        ctx.runOnClient(c->{var s=c.gui.screen();s.resize(s.width,s.height);if(((JsonArray)get(s,"quests")).size()!=5)throw new AssertionError("Reload duplicated quests");click(s,250,230,InputConstants.MOUSE_BUTTON_LEFT);if(!(boolean)get(s,"editMode"))throw new AssertionError("Brush toggle");});
        ctx.waitTicks(3);ctx.takeScreenshot("quest-editor-plus-sixth-slot");
        ctx.runOnClient(c->{click(c.gui.screen(),15+5*36+18,124,InputConstants.MOUSE_BUTTON_LEFT);if(!(c.gui.screen() instanceof MpsqQuestsScreen))throw new AssertionError("Add slot");((EditBox)get(c.gui.screen(),"titleField")).setValue("Neue Quest");((EditBox)get(c.gui.screen(),"descriptionField")).setValue("Eine Testaufgabe");});ctx.waitTicks(2);ctx.takeScreenshot("quest-create-editor");
        ctx.runOnClient(c->button(c.gui.screen(),"Quest speichern"));ctx.waitTicks(3);
        ctx.runOnClient(c->{if(MpsqLocalWorldStore.array("quests").size()!=6)throw new AssertionError("Create failed");click(c.gui.screen(),33,124,InputConstants.MOUSE_BUTTON_LEFT);((EditBox)get(c.gui.screen(),"titleField")).setValue("Bearbeitet");button(c.gui.screen(),"Quest speichern");});ctx.waitTicks(3);
        ctx.runOnClient(c->{if(!MpsqLocalWorldStore.array("quests").get(0).getAsJsonObject().get("title").getAsString().equals("Bearbeitet"))throw new AssertionError("Edit/order");click(c.gui.screen(),33,124,InputConstants.MOUSE_BUTTON_RIGHT);if(!(c.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen))throw new AssertionError("Delete needs confirmation");});ctx.waitTicks(3);ctx.takeScreenshot("quest-delete-confirmation");
        ctx.runOnClient(c->{c.gui.screen().onClose();if(MpsqLocalWorldStore.array("quests").size()!=6)throw new AssertionError("Cancel deleted quest");});ctx.waitTicks(3);
        ctx.runOnClient(c->{click(c.gui.screen(),33,124,InputConstants.MOUSE_BUTTON_RIGHT);var s=c.gui.screen();var buttons=s.children().stream().filter(w->w instanceof Button).map(w->(Button)w).toList();buttons.get(0).onPress(null);});ctx.waitTicks(3);
        ctx.runOnClient(c->{if(MpsqLocalWorldStore.array("quests").size()!=5)throw new AssertionError("Delete failed");TeamStateStore.setSelf(new TeamProfile(java.util.UUID.randomUUID(),"Player",TeamRank.values()[0],null,true));if(MpsqQuestMenuScreen.canEdit())throw new AssertionError("Low rank edit permission");});
        ctx.setScreen(()->new MpsqQuestMenuScreen(null,"quest-test-npc"));ctx.waitTicks(3);ctx.takeScreenshot("quest-player-no-brush");
        ctx.runOnClient(c->{click(c.gui.screen(),250,230,InputConstants.MOUSE_BUTTON_LEFT);if((boolean)get(c.gui.screen(),"editMode"))throw new AssertionError("Hidden brush clickable");TeamStateStore.setSelf(new TeamProfile(java.util.UUID.randomUUID(),"GuiCheck",TeamRank.SENIOR_OFFICER,null,true));var all=new JsonArray();for(int i=0;i<27;i++)all.add(quest(i));MpsqLocalWorldStore.setArray("quests",all);});
        ctx.setScreen(()->new MpsqQuestMenuScreen(null,"quest-test-npc"));ctx.waitTicks(3);
        ctx.runOnClient(c->{click(c.gui.screen(),250,230,InputConstants.MOUSE_BUTTON_LEFT);c.gui.screen().mouseScrolled(0,0,0,-1);});ctx.waitTicks(3);ctx.takeScreenshot("quest-full-page-add-next-page");
        ctx.runOnClient(c->{if((int)get(c.gui.screen(),"page")!=1)throw new AssertionError("No add page for full board");click(c.gui.screen(),33,124,InputConstants.MOUSE_BUTTON_LEFT);if(!(c.gui.screen() instanceof MpsqQuestsScreen))throw new AssertionError("Next page add");MpsqLocalWorldStore.setArray("quests",new JsonArray());});
        ctx.setScreen(()->new MpsqQuestMenuScreen(null,"quest-test-npc"));ctx.waitTicks(3);
        ctx.runOnClient(c->{var s=c.gui.screen();int originalW=s.width,originalH=s.height;for(int[] size:new int[][]{{320,180},{427,240},{854,480}}){s.resize(size[0],size[1]);float scale=(float)get(s,"drawScale");int left=(int)get(s,"drawLeft"),top=(int)get(s,"drawTop");if(left<15||top<15||left+Math.round(352*scale)>s.width-15||top+Math.round(256*scale)>s.height-15)throw new AssertionError("Quest frame clipped");}s.resize(originalW,originalH);click(s,250,230,InputConstants.MOUSE_BUTTON_LEFT);if(!(boolean)get(s,"editMode"))throw new AssertionError("Empty-state text blocked brush");click(s,30,230,InputConstants.MOUSE_BUTTON_LEFT);if(c.gui.screen()!=null)throw new AssertionError("Back button blocked");});
        System.out.println("QUEST_GUI_SMOKE_PASS");
    }
}
