package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Lightweight BetterDialogues-inspired actionbar dialogue: typewriter reveal and F-to-advance. */
public final class MpsqDialogueManager {
    private static final List<String> pages=new ArrayList<>();
    private static KeyMapping advance;
    private static int page,visibleChars,ticks;
    private static boolean active;
    static boolean active(){return active;}
    private static BiConsumer<String, Boolean> completion;
    private MpsqDialogueManager(){}
    public static void initialize(){
        advance=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mpsqcamera.dialogue_next",com.mojang.blaze3d.platform.InputConstants.KEY_F,MpsqKeyCategories.MAIN));
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(!TeamVisibilitySettings.inArea()){active=false;completion=null;pages.clear();return;}if(!active||client.player==null)return;ticks++;if(ticks%2==0&&page<pages.size())visibleChars=Math.min(visibleLength(pages.get(page)),visibleChars+1);while(advance.consumeClick())next();});
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera", "dialogue"), (context,tickDelta)->render(context));
    }
    public static void start(JsonObject data){start(data,null);}
    public static void start(JsonObject data, BiConsumer<String, Boolean> onComplete){pages.clear();completion=onComplete;JsonArray input=data.getAsJsonArray("pages");if(input==null)return;for(JsonElement e:input)if(e.isJsonPrimitive()){String line=e.getAsString().trim();if(!line.isEmpty())pages.add(line);}page=0;visibleChars=0;ticks=0;active=!pages.isEmpty();if(active&&pages.size()==1)completeTutorial();}
    private static void next(){if(page>=pages.size())return;int length=visibleLength(pages.get(page));if(visibleChars<length){visibleChars=length;return;}page++;visibleChars=0;if(page>=pages.size())active=false;else if(page==pages.size()-1)completeTutorial();}
    private static void completeTutorial(){if(completion!=null){var callback=completion;completion=null;callback.accept("complete",true);}}
    private static void render(GuiGraphicsExtractor c){if(!active||!TeamVisibilitySettings.inArea())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||pages.isEmpty())return;int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),boxW=Math.min(520,w-32),left=(w-boxW)/2,top=h-82;String raw=pages.get(page),line=typedPrefix(raw,visibleChars);c.fill(left,top,left+boxW,top+54,0xD9000000);c.fill(left,top,left+boxW,top+2,0xFFFF6677);c.text(mc.font,TeamChatText.fromAmpersandCodes(line,net.minecraft.ChatFormatting.WHITE),left+12,top+13,0xFFFFFFFF);String action=visibleChars<visibleLength(raw)?"F  Text anzeigen":"F  "+(page+1<pages.size()?"Weiter":"Schließen");c.text(mc.font,Component.literal(action),left+12,top+37,0xFFCCCCCC);}
    private static int visibleLength(String raw){int count=0;for(int i=0;i<raw.length();i++){if(isColorCodeAt(raw,i)){i++;continue;}count++;}return count;}
    private static String typedPrefix(String raw,int targetVisible){StringBuilder out=new StringBuilder();int shown=0;for(int i=0;i<raw.length();i++){if(isColorCodeAt(raw,i)){out.append(raw.charAt(i)).append(raw.charAt(++i));continue;}if(shown>=targetVisible)break;out.append(raw.charAt(i));shown++;}return out.toString();}
    private static boolean isColorCodeAt(String raw,int index){if(index+1>=raw.length()||raw.charAt(index)!='&')return false;char code=Character.toLowerCase(raw.charAt(index+1));return "0123456789abcdefklmnorghijpq".indexOf(code)>=0;}
}
