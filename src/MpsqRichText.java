package de.galaxushd.mpsqcamera;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
/** Legacy ampersand colours converted to real styled text, including wrapping. */
public final class MpsqRichText {
    public static Component parse(String value){
        MutableComponent result=Component.empty();Style style=Style.EMPTY;StringBuilder run=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);ChatFormatting format=(c=='&'||c=='§')&&i+1<value.length()?ChatFormatting.getByCode(Character.toLowerCase(value.charAt(i+1))):null;
            if(format==null){run.append(c);continue;}
            if(!run.isEmpty()){result.append(Component.literal(run.toString()).setStyle(style));run.setLength(0);}i++;
            style=format==ChatFormatting.RESET?Style.EMPTY:"0123456789abcdef".indexOf(Character.toLowerCase(value.charAt(i)))>=0?Style.EMPTY.applyFormat(format):style.applyFormat(format);
        }
        if(!run.isEmpty())result.append(Component.literal(run.toString()).setStyle(style));return result;
    }
}
