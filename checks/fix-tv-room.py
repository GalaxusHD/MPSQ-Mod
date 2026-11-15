from pathlib import Path
import json
p=Path('src/client/java/de/galaxushd/mpsqcamera')
f=p/'MpsqGamesRuntime.java';s=f.read_text();a=s.index('                m.rotate(ctx.camera()');b=s.index('\n            }',a)
s=s[:a]+'''                // Vanilla name tags provide the correct facing and through-wall pass.
                // submitNameTag adds half a block; compensate to center the text on XYZ.
                ctx.consumers().collector().submitNameTag(m,new net.minecraft.world.phys.Vec3(0,-.5+c.font.lineHeight*.0125,0),0,text,true,0xF000F0,ctx.consumers().camera());m.popPose();'''+s[b:];f.write_text(s)
f=p/'MpsqTvFurniture.java';s=f.read_text();s=s.replace('    private static int ticks;','    private static int ticks;\n    private static Hit pendingEditor;')
s=s.replace('if(level!=c.level){level=c.level;placements.clear();videoLinks.clear();ticks=0;}if(c.level!=null&&++ticks%20==0)reload();','''if(level!=c.level){level=c.level;placements.clear();videoLinks.clear();pendingEditor=null;ticks=0;}
            if(c.level==null)return;
            if(++ticks%20==0)reload();
            if(pendingEditor!=null&&c.gui.screen()==null){Hit hit=pendingEditor;pendingEditor=null;c.gui.setScreen(new MpsqTvButtonScreen(hit));}''')
s=s.replace('new AABB(left[i],.25,6.75,left[i]+.5,.75,7.25).clip(from,to)','new AABB(left[i]-.08,.17,6.67,left[i]+.58,.83,7.33).clip(from,to)')
s=s.replace('public static boolean configureTarget(){Hit hit=target();','public static boolean configureTarget(){reload();Hit hit=target();')
s=s.replace('Minecraft.getInstance().gui.setScreen(new MpsqTvButtonScreen(hit));return true;','pendingEditor=hit;return true;')
f.write_text(s)
source=json.loads(Path('C:/Users/lanck_m63m7au/Downloads/tv.bbmodel').read_text())
f=Path('src/client/resources/assets/mpsqcamera/custom_models/furniture/mpsq_tv.json');m=json.loads(f.read_text())
for dst,src in zip(m['elements'],source['elements']):
 for side,face in src['faces'].items():
  dst['faces'][side]['uv']=[v/source['resolution']['width' if i%2==0 else 'height'] for i,v in enumerate(face['uv'])]
f.write_text(json.dumps(m,separators=(',',':'))+'\n')
f=Path('checks/TvModelCheck.java');s=f.read_text();s=s.replace('        Class<?> renderer=', '''        JsonArray frameUv=model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
        check(Math.abs(frameUv.get(0).getAsDouble()-.0625)<1e-8&&Math.abs(frameUv.get(3).getAsDouble()-.5)<1e-8,"Java-block UVs use the project 8x8 canvas, not stale per-texture 128x128 metadata");
        JsonObject back=model.getAsJsonArray("elements").get(2).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("south");
        check(Math.abs(back.getAsJsonArray("uv").get(0).getAsDouble()-.234375)<1e-8,"Back panel samples its gray atlas rectangle");
        Class<?> renderer=''');f.write_text(s)
