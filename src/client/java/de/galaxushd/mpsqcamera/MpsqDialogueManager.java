package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Lightweight BetterDialogues-inspired actionbar dialogue: typewriter reveal and F-to-advance. */
public final class MpsqDialogueManager {
    private static final List<String> pages=new ArrayList<>();
    private static KeyBinding advance;
    private static int page,visibleChars,ticks;
    private static boolean active;
    private static BiConsumer<String, Boolean> completion;
    private MpsqDialogueManager(){}
    public static void initialize(){
        advance=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mpsqcamera.dialogue_next",GLFW.GLFW_KEY_F,"category.mpsqcamera.main"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(!active||client.player==null)return;ticks++;if(ticks%2==0&&page<pages.size())visibleChars=Math.min(pages.get(page).length(),visibleChars+1);while(advance.wasPressed())next();});
        HudRenderCallback.EVENT.register((context,tickDelta)->render(context));
    }
    public static void start(JsonObject data){start(data,null);}
    public static void start(JsonObject data, BiConsumer<String, Boolean> onComplete){pages.clear();completion=onComplete;JsonArray input=data.getAsJsonArray("pages");if(input==null)return;for(JsonElement e:input)if(e.isJsonPrimitive()){String line=e.getAsString().trim();if(!line.isEmpty())pages.add(line);}page=0;visibleChars=0;ticks=0;active=!pages.isEmpty();if(active&&pages.size()==1)completeTutorial();}
    private static void next(){if(page>=pages.size())return;if(visibleChars<pages.get(page).length()){visibleChars=pages.get(page).length();return;}page++;visibleChars=0;if(page>=pages.size())active=false;else if(page==pages.size()-1)completeTutorial();}
    private static void completeTutorial(){if(completion!=null){var callback=completion;completion=null;callback.accept("complete",true);}}
    private static void render(DrawContext c){if(!active)return;MinecraftClient mc=MinecraftClient.getInstance();if(mc.player==null||pages.isEmpty())return;int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight(),boxW=Math.min(520,w-32),left=(w-boxW)/2,top=h-82;String line=pages.get(page).substring(0,Math.min(visibleChars,pages.get(page).length()));c.fill(left,top,left+boxW,top+54,0xD9000000);c.fill(left,top,left+boxW,top+2,0xFFFF6677);c.drawTextWithShadow(mc.textRenderer,TeamChatText.fromAmpersandCodes(line,net.minecraft.util.Formatting.WHITE),left+12,top+13,0xFFFFFFFF);String action=visibleChars<pages.get(page).length()?"F  Text anzeigen":"F  "+(page+1<pages.size()?"Weiter":"Schließen");c.drawTextWithShadow(mc.textRenderer,Text.literal(action),left+12,top+37,0xFFCCCCCC);}
}
