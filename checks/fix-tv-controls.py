from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera')
f=p/'MpsqTvFurniture.java';s=f.read_text(encoding='utf-8');s=s.replace('Hit found=null;double[] left={-6.75,-6,-5.25,20.25,21.25,22.25};','Hit found=null;');s=s.replace('for(int i=0;i<left.length;i++){','for(int i=0;i<6;i++){').replace('new AABB(left[i]-.08,.17,6.67,left[i]+.58,.83,7.33).clip(from,to)','buttonBounds(i).clip(from,to)');s=s.replace('    public static Hit target(){','''    /** Logical IDs stay stable in saved actions: pink 1/2/3, then back/pause/forward.
     * Positions follow the visible symbols in the current TV texture atlas. */
    static AABB buttonBounds(int button){
        double[] left={22.25,21.25,20.25,-5.25,-6,-6.75};
        if(button<0||button>=left.length)throw new IllegalArgumentException("TV button");
        return new AABB(left[button]-.08,.17,6.67,left[button]+.58,.83,7.33);
    }
    public static Hit target(){''');f.write_text(s,encoding='utf-8')
f=p/'ScreenRenderer.java';s=f.read_text(encoding='utf-8').replace('STATUS_OVERLAY_ALPHA = 230','STATUS_OVERLAY_ALPHA = 255');f.write_text(s,encoding='utf-8')
f=p/'ScreenStatusTextures.java';s=f.read_text(encoding='utf-8').replace('if ((pixel >>> 24) < MIN_ALPHA) image.setPixel(x, y, 0);','image.setPixel(x, y, brighten(pixel));');s=s.replace('    private static Identifier load(Identifier source) {','''    static int brighten(int pixel) {
        int alpha=pixel>>>24;
        if(alpha<MIN_ALPHA)return 0;
        int r=Math.min(255,Math.round(((pixel>>>16)&255)*1.6f));
        int g=Math.min(255,Math.round(((pixel>>>8)&255)*1.6f));
        int b=Math.min(255,Math.round((pixel&255)*1.6f));
        return (alpha<<24)|(r<<16)|(g<<8)|b;
    }
    private static Identifier load(Identifier source) {''');f.write_text(s,encoding='utf-8')
f=p/'CinemaBrowserManager.java';s=f.read_text(encoding='utf-8').replace('            ticks++;','            BROWSERS.values().forEach(session -> removeLegacyCaptionShield(session.browser()));\n            ticks++;');s=s.replace('    private static boolean hasPlayingCinemaScreen() {','''    /** Older hosted player pages hide already-playing video behind a caption shield.
     * Remove only that MPSQ overlay, including when it is inserted after page load.
     * The observer runs before the next paint; subtitles can still be disabled normally. */
    private static void removeLegacyCaptionShield(MCEFBrowser browser) {
        try {
            browser.getCefBrowser().executeJavaScript("""
                (()=>{
                  if(location.hostname!=='www.mixelpixel-squidgame.net'&&location.hostname!=='mixelpixel-squidgame.net')return;
                  if(location.pathname!=='/mpsq-player.html'||window.__mpsqVisibleVideo)return;
                  window.__mpsqVisibleVideo=true;
                  const reveal=()=>document.getElementById('caption-shield')?.remove();
                  new MutationObserver(reveal).observe(document,{childList:true,subtree:true});
                  reveal();
                })();
                """,browser.getCefBrowser().getURL(),0);
        } catch(RuntimeException error) {
            MpsqCameraClient.LOGGER.debug("Video page is still loading",error);
        }
    }
    private static boolean hasPlayingCinemaScreen() {''');f.write_text(s,encoding='utf-8')
f=Path('checks/TvModelCheck.java');s=f.read_text(encoding='utf-8').replace('        Method inverse=', '''        Method hitbox=MpsqTvFurniture.class.getDeclaredMethod("buttonBounds",int.class);hitbox.setAccessible(true);
        // Element indices selected from the visible atlas glyphs: pink 1,2,3 and gray back,pause,forward.
        int[] buttonElements={5,6,10,8,9,7};
        for(int i=0;i<6;i++){
            JsonObject cube=model.getAsJsonArray("elements").get(buttonElements[i]).getAsJsonObject();
            double x=(cube.getAsJsonArray("from").get(0).getAsDouble()+cube.getAsJsonArray("to").get(0).getAsDouble())/3;
            var bounds=(net.minecraft.world.phys.AABB)hitbox.invoke(null,i);
            check(bounds.contains(x,.5,7),"Action hits the rendered numbered/transport button "+i);
            for(int j=0;j<6;j++)if(i!=j)check(!((net.minecraft.world.phys.AABB)hitbox.invoke(null,j)).contains(x,.5,7),"No crossed button action");
            check(cube.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString().equals(i<3?"1":"3"),"Pink configurable; gray transport");
        }
        Class<?> status=Class.forName("de.galaxushd.mpsqcamera.ScreenStatusTextures");
        Method brighten=status.getDeclaredMethod("brighten",int.class);brighten.setAccessible(true);
        check((int)brighten.invoke(null,0x40FFFFFF)==0,"Transparent residue stays invisible");
        check((int)brighten.invoke(null,0xFF808080)==0xFFCDCDCD,"Status titles are brighter with unchanged opacity");
        Method inverse=''');f.write_text(s,encoding='utf-8')
