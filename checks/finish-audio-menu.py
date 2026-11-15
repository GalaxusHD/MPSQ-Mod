from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera');f=p/'ModSettingsScreen.java';s=f.read_text(encoding='utf-8').replace('addRenderableWidget(new LautstaerkeSlider(cx - btnW / 2, y, btnW, 20, ModConfig.volume));','addRenderableWidget(Button.builder(Component.literal("Minecraft-Lautstärke"), b -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.SoundOptionsScreen(this,minecraft.options))).bounds(cx-btnW/2,y,btnW,20).build());');a=s.index('    /** Lautstärke-Slider');s=s[:a]+'}\n';f.write_text(s,encoding='utf-8')
f=p/'CinemaAudioManager.java';s=f.read_text(encoding='utf-8');a=s.index('        execute(browser, "(()=>');b=s.index('\n    private static void silence',a);s=s[:a]+'''        execute(browser, volumeScript(volume));
    }
    static String volumeScript(String volume) {
        return """
            (()=>{const apply=d=>{
              d.querySelectorAll('video,audio').forEach(e=>{e.volume=__GAIN__;});
              d.querySelectorAll('iframe').forEach(f=>{
                try{if(f.contentDocument)apply(f.contentDocument)}catch(e){}
                try{const u=new URL(f.src);
                  if(u.hostname==='www.youtube.com'||u.hostname==='www.youtube-nocookie.com')
                    f.contentWindow.postMessage(JSON.stringify({event:'command',func:'setVolume',args:[__GAIN__*100]}),u.origin);
                }catch(e){}
              });
            };apply(document)})()
            """.replace("__GAIN__",volume);
    }
'''+s[b:];f.write_text(s,encoding='utf-8')
