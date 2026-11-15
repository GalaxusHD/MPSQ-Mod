$base='src/client/java/de/galaxushd/mpsqcamera/'
function Edit($file,$old,$new){$p=$base+$file+'.java';$s=[IO.File]::ReadAllText((Join-Path (Get-Location) $p));if(!$s.Contains($old)){throw "Missing text in $file : $old"};[IO.File]::WriteAllText((Join-Path (Get-Location) $p),$s.Replace($old,$new))}
Edit 'MpsqAccessoryRenderer' 'w>1024||h>1024' 'w>(isBuiltinModel(url)?2048:1024)||h>(isBuiltinModel(url)?2048:1024)'
Edit 'MpsqAccessoryRenderer' 'pixels>2097152' 'pixels>(isBuiltinModel(url)?8388608:2097152)'
Edit 'MpsqAccessoryRenderer' 'matrices.translate(-bounds.centerX(),-(bounds.minY()+bounds.maxY())*.5f,-bounds.centerZ());drawBbModel' 'matrices.translate(-bounds.centerX(),-(bounds.minY()+bounds.maxY())*.5f-(id.equals("mpsq_baby_happy_ghast")?9:0),-bounds.centerZ());drawBbModel'
Edit 'MpsqAccessoryRenderer' 'timer.color' 'timer.displayColor()'
Edit 'MpsqSceneWorldNpcs' 'entity.setInvisible(MpsqScenesClips.get(pose.clip())!=null);' 'entity.setInvisible(MpsqScenesClips.get(pose.clip())!=null);if(MpsqScenesClips.get(pose.clip())!=null){entity.setGlowingTag(false);entity.removeTag("mpsq_npc_glow_on");}'
Edit 'MpsqCinematicActors' 'active.setInvisible(MpsqScenesClips.get(clip)!=null);' 'active.setInvisible(MpsqScenesClips.get(clip)!=null);if(MpsqScenesClips.get(clip)!=null){active.setGlowingTag(false);active.removeTag("mpsq_npc_glow_on");}'
Edit 'MpsqScenesScreen' '30+((index-27)%7)*18:10+(index%9)*18' '31+((index-27)%7)*18:11+(index%9)*18'
Edit 'MpsqActionSetupScreen' '"Violett (Standard)","Pink (#ec2f53)","Rot (#cf2020)"' '"Violett","Pink","Rot"'
Edit 'MpsqMobIcons' '1280,1280,1280,1280' '1080,1080,1080,1080'
Copy-Item -LiteralPath 'C:/Users/lanck_m63m7au/Downloads/Illusionisten-Spawn-Ei im Pixelstil (1).png' -Destination 'src/main/resources/assets/mpsqcamera/textures/gui/spawn_eggs/illusioner.png'
Copy-Item -LiteralPath 'C:/Users/lanck_m63m7au/Downloads/Pixelart-Spawn-Ei in Grün und Lila (1).png' -Destination 'src/main/resources/assets/mpsqcamera/textures/gui/spawn_eggs/giant.png'
Copy-Item -LiteralPath 'C:/Users/lanck_m63m7au/Downloads/crate-tag-2026-11-10T03-53-28.png' -Destination 'src/main/resources/assets/mpsqcamera/textures/gui/npc_tags/crate.png'
Edit 'MpsqAccessoryRenderer' 'String furnitureName=o.has("display_name")' 'if(MpsqCrate.isCrate(o))drawBillboard(context,matrices,consumers,Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/npc_tags/crate.png"),x+0.5-camera.x,y+1.35-camera.y,z+0.5-camera.z,1.2f,.343f);String furnitureName=o.has("display_name")'
Get-ChildItem src/main/resources/assets/mpsqcamera/lang -Filter *.json | ForEach-Object {$s=[IO.File]::ReadAllText($_.FullName);$s=[regex]::Replace($s,'"category.mpsqcamera.main":\s*"[^"]*"','"category.mpsqcamera.main": "MPSQ"');$j=$s|ConvertFrom-Json;$j|Add-Member -Force NoteProperty 'key.category.mpsqcamera.main' 'MPSQ';$j|Add-Member -Force NoteProperty 'key.category.mpsqcamera.replay' 'MPSQ Replay';$j|Add-Member -Force NoteProperty 'category.mpsqcamera.replay' 'MPSQ Replay';[IO.File]::WriteAllText($_.FullName,($j|ConvertTo-Json -Depth 20))}
Edit 'MpsqModularTimer' 'system="";' 'system="",endInput="";'
Edit 'MpsqModularTimer' 'boolean bossbar=true,tickSound=false;' 'boolean bossbar=false,tickSound=false,redEnding=false;'
Edit 'MpsqModularTimer' 'String digits(){return MpsqTimerClock.digits(clock.seconds(),format);}' 'String digits(){return MpsqTimerClock.digits(clock.seconds(),"auto");} int displayColor(){return redEnding&&!clock.up&&clock.seconds()<=10?0xFFFF3030:color;}'
$p=$base+'MpsqModularTimer.java';$s=[IO.File]::ReadAllText((Join-Path (Get-Location) $p));$start=$s.IndexOf('        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry');$end=$s.IndexOf('        ClientCommandRegistrationCallback',$start);$s=$s.Remove($start,$end-$start);[IO.File]::WriteAllText((Join-Path (Get-Location) $p),$s)
Edit 'MpsqModularTimer' 'o.addProperty("name",t.name);' 'o.addProperty("name",t.name);o.addProperty("endInput",t.endInput);o.addProperty("redEnding",t.redEnding);'
Edit 'MpsqModularTimer' 't.format=o.get("format").getAsString();' 't.format="auto";t.endInput=o.has("endInput")?o.get("endInput").getAsString():"";t.redEnding=o.has("redEnding")&&o.get("redEnding").getAsBoolean();'
Edit 'MpsqModularTimer' 't.bossbar=o.get("bossbar").getAsBoolean();' 't.bossbar=false;'
Edit 'MpsqModularTimer' 'Component.literal("Timer "+t.name+" ist abgelaufen.")' 'Component.literal(t.endInput.isBlank()?"Timer "+t.name+" ist abgelaufen.":t.endInput)'
Edit 'MpsqModularTimer' 'sound(c,t,net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,1);' 'if(t.end.equals("sound")&&!t.endInput.isBlank()){MpsqMediaAudioManager.playAuto(t.endInput);return;}sound(c,t,net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,1);'
Edit 'MpsqTimerScreen' 'name,time,x,y,z,yaw,pitch,scale,color;' 'name,time,x,y,z,yaw,pitch,scale,color,endInput;'
Edit 'MpsqTimerScreen' 'name=field("timer"+(all.size()+1),"Name",left,bottom,140);time=field("5m","Zeit",left+145,bottom,65);' 'name=field("","Name",left,bottom,210);'
Edit 'MpsqTimerScreen' 'MpsqModularTimer.create(name.getValue(),MpsqTimerClock.parse(time.getValue()))' 'MpsqModularTimer.create(name.getValue(),300000)'
Edit 'MpsqTimerScreen' 'i<3' 'i<4'
Edit 'MpsqTimerScreen' 'List.of("Steuerung","Darstellung","Position").get(i),left+i*101,42,98' 'List.of("Steuerung","Darstellung","Position","Ablauf").get(i),left+i*76,42,73'
Edit 'MpsqTimerScreen' 'button("Format: "+timer.format,left,80,145,()->{timer.format=next(timer.format,List.of("auto","minutes","hours"));commit();});button("Bossbar: "+on(timer.bossbar),left+150,80,150,()->{timer.bossbar=!timer.bossbar;commit();});' 'button("Letzte 10 Sekunden rot: "+on(timer.redEnding),left,80,300,()->{timer.redEnding=!timer.redEnding;commit();});'
Edit 'MpsqTimerScreen' '            }else{' '            }else if(tab==2){'
Edit 'MpsqTimerScreen' 'button("Timer löschen",left,172,300,()->{MpsqModularTimer.change(timer,"remove",0);timer=null;refresh();});' 'button("Timer löschen",left,172,300,()->{MpsqModularTimer.change(timer,"remove",0);timer=null;refresh();});
            }else{
                button("Ende: "+endLabel(timer.end),left,80,300,()->{timer.end=next(timer.end,List.of("sound","message","firework","stop_game","none"));commit();});
                endInput=field(timer.endInput,timer.end.equals("message")?"Nachricht":timer.end.equals("firework")?"minecraft (LightTheNight folgt)":"Minecraft- oder MPSQ-Sound-ID",left,110,300);endInput.setMaxLength(240);
                button("Ablauf speichern",left,140,300,()->{if(timer.end.equals("firework")&&!endInput.getValue().isBlank()&&!endInput.getValue().equalsIgnoreCase("minecraft"))throw new IllegalArgumentException("LightTheNight folgt; derzeit minecraft verwenden.");timer.endInput=endInput.getValue().trim();commit();});'
