from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera')
f=p/'MpsqSkyRound.java'
s=f.read_text();s=s.replace('/** Fixed first-tower roster; subsequent buttons only arm the surviving roster. */','/** Activation freezes the roster; tower buttons only arm its survivors. */')
a=s.index('    public boolean press');b=s.index('        running = true;',a)
s=s[:a]+'''    public void begin(Set<UUID> participants) {
        reset();
        roster.addAll(participants);
    }
    public boolean press(int index, Set<UUID> inside, long now) {
        if (running || tower >= 3 || index != tower) return false;
        Set<UUID> remaining = survivors();
        if (remaining.isEmpty() || !inside.containsAll(remaining)) return false;
'''+s[b:]
a=s.index('    public void kick');b=s.index('    private void advance',a)
s=s[:a]+'''    /** Falls remain fatal during green transit and after the final tower. */
    public void fall(Set<UUID> ids) {
        Set<UUID> losses = survivors();
        losses.retainAll(ids);
        if (losses.isEmpty()) return;
        out.addAll(losses);
        if (running) advance();
        else if (survivors().isEmpty()) tower = 3;
    }
    public void kick(UUID id) { fall(Set.of(id)); }
'''+s[b:];f.write_text(s)
f=p/'MpsqGamesRuntime.java';s=f.read_text()
s=s.replace('private static final Set<net.minecraft.core.BlockPos> skyPowered = new HashSet<>();\n    private static final Map<net.minecraft.core.BlockPos,Long> skyPending=new HashMap<>();','private static final Set<String> excludedNames = new HashSet<>();')
a=s.index('        net.fabricmc.fabric.api.event.player.UseBlockCallback');b=s.index('    public static void height',a)
s=s[:a]+'    }\n'+s[b:]
s=s.replace('stop(); mode=id; message', '''stop(); mode=id;
        if(id.equals("sky_squid")){
            Set<UUID> roster=new LinkedHashSet<>();var level=Minecraft.getInstance().level;
            if(level!=null)for(Entity e:level.entitiesForRendering())if(eligible(e)&&MpsqZoneCatalog.inMaster(e))roster.add(e.getUUID());
            skyRound.begin(roster);roster.forEach(uuid->glows.put(uuid,WHITE));
        }
        message''')
s=s.replace('registered.clear();excluded.clear();','registered.clear();').replace('skyRound.reset();skyPowered.clear();skyPending.clear();','skyRound.reset();')
s=s.replace('    public static boolean participant(Player p){','    public static void resetSession(){stop();excluded.clear();excludedNames.clear();}\n    public static boolean participant(Player p){')
s=s.replace('if(p.isSpectator()||excluded.contains(p.getUUID()))','if(p.isSpectator()||excluded.contains(p.getUUID())||excludedNames.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT)))')
s=s.replace('        MpsqMovementSensorSystem.kick(name);','        excludedNames.add(name.toLowerCase(Locale.ROOT));\n        MpsqMovementSensorSystem.kick(name);')
a=s.index('    public static void skyButtonUpdate');b=s.index('    private static InteractionResult hit',a)
s=s[:a]+'''    // Block updates alone must not start a tower: only configured MPSQ actions do.
    public static void skyButtonUpdate(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {}
    public static void startSkyTower(net.minecraft.core.BlockPos pos){
        if(!mode.equals("sky_squid")||!TeamVisibilitySettings.visible()){message("Bitte zuerst Sky Squid Game aktivieren.");return;}
        var c=Minecraft.getInstance();if(c.level==null)return;
        var zones=MpsqZoneCatalog.forGame("sky_squid");
        int tower=skyRound.tower();if(zones.size()!=3||tower>=3||skyRound.running())return;
        var zone=zones.get(tower).box();
        if(!MpsqZoneCatalog.inMaster(pos)||!zone.containsFeet(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)){
            message("Dieser Knopf muss im aktuellen Turm "+(tower+1)+" liegen.");return;
        }
        Set<UUID> inside=new HashSet<>();
        for(Entity e:c.level.entitiesForRendering())if(eligible(e)&&zone.containsFeet(e)&&e.getY()>=height)inside.add(e.getUUID());
        if(skyRound.press(tower,inside,System.currentTimeMillis())){
            for(UUID id:skyRound.survivors())glows.put(id,WHITE);
            message("Sky Squid Game: Turm "+(tower+1)+" gestartet – 15 Minuten, "+skyRound.survivors().size()+" Teilnehmer.");
        }else{
            Set<UUID> missing=skyRound.survivors();missing.removeAll(inside);
            message(skyRound.survivors().isEmpty()?"Sky Squid Game: Keine verbleibenden Teilnehmer.":"Sky Squid Game: In Turm "+(tower+1)+" fehlen noch "+missing.size()+" Teilnehmer.");
        }
    }
    private static void sky(List<? extends Entity> players,List<MpsqZoneCatalog.Entry> zones){
        if(zones.size()!=3)return;
        long now=System.currentTimeMillis();int previousTower=skyRound.tower();
        // Collect all losses together, so simultaneous falls cannot skip a tower.
        Set<UUID> losses=new HashSet<>();
        for(Entity p:players)if(p.getY()<height)losses.add(p.getUUID());
        if(skyRound.running()){
            var zone=zones.get(skyRound.tower()).box();
            for(Entity p:players)if(!zone.containsFeet(p))losses.add(p.getUUID());
            skyRound.tick(losses,now);
        }
        skyRound.fall(losses);
        if(skyRound.tower()!=previousTower){
            if(skyRound.survivors().isEmpty())message("Sky Squid Game: Runde beendet – keine verbleibenden Teilnehmer.");
            else if(skyRound.tower()==3)message("Sky Squid Game: Finale bestanden.");
            else message("Sky Squid Game: Turm bestanden. Weiter zu Turm "+(skyRound.tower()+1)+" und dort den Knopf drücken.");
        }
        for(UUID id:skyRound.eliminated())if(!excluded.contains(id))eliminate(id);
        for(UUID id:skyRound.survivors())if(!excluded.contains(id)){
            boolean arrived=skyRound.tower()<3&&players.stream().anyMatch(p->p.getUUID().equals(id)&&zones.get(skyRound.tower()).box().containsFeet(p));
            glows.put(id,skyRound.running()||skyRound.tower()==0||arrived?WHITE:GREEN);
        }
        if(skyRound.running())bar("Sky Squid Game · Turm "+(skyRound.tower()+1)+": "+((skyRound.remaining(now)+999)/1000)+" s",skyRound.remaining(now)/(float)MpsqSkyRound.DURATION);
        else MpsqBossbarManager.remove("game_round");
    }
'''+s[b:];f.write_text(s)
f=p/'MpsqFinishZoneTool.java';s=f.read_text().replace('MpsqSystemController.stopActive();MpsqZoneCatalog.load();','MpsqSystemController.stopActive();MpsqGamesRuntime.resetSession();MpsqZoneCatalog.load();');f.write_text(s)
f=p/'MpsqActionSetupScreen.java';s=f.read_text();s=s.replace('"SHOW_DIALOGUE","OPEN_LINK","SWITCH_SYSTEM","RLGL_GREEN"};','"SHOW_DIALOGUE","OPEN_LINK","SWITCH_SYSTEM","RLGL_GREEN","SKY_SQUID_START"};')
s=s.replace('systemId.equals("rope_jump")','systemId.equals("rope_jump")||systemId.equals("sky_squid")').replace('selected.id().equals("rope_jump")','selected.id().equals("rope_jump")||selected.id().equals("sky_squid")')
s=s.replace('!selected.startsWith("RLGL_")','!selected.startsWith("RLGL_")&&!selected.equals("SKY_SQUID_START")')
s=s.replace('case "RLGL_GREEN"->"Spielphase: Grün + Countdown";','case "RLGL_GREEN"->"Spielphase: Grün + Countdown";\n        case "SKY_SQUID_START"->"Sky Squid Game: Turm starten (15 Min.)";')
s=s.replace('"SHOW_DIALOGUE".equals(selected)?"Textseiten mit || trennen":','"SKY_SQUID_START".equals(selected)?"Startet den aktuellen Turm; Knopf muss innerhalb seiner Zone liegen":\n                "SHOW_DIALOGUE".equals(selected)?"Textseiten mit || trennen":')
f.write_text(s)
f=p/'MpsqActionSync.java';s=f.read_text().replace('systemId.equals("rope_jump")','systemId.equals("rope_jump")||systemId.equals("sky_squid")');s=s.replace('            case "RLGL_GREEN" ->', '''            case "SKY_SQUID_START" -> {
                if(event.has("trigger_position")){
                    JsonObject pos=event.getAsJsonObject("trigger_position");
                    MpsqGamesRuntime.startSkyTower(new net.minecraft.core.BlockPos(pos.get("x").getAsInt(),pos.get("y").getAsInt(),pos.get("z").getAsInt()));
                }
            }
            case "RLGL_GREEN" ->''');f.write_text(s)
f=p/'MpsqTriggerManager.java';s=f.read_text().replace('return "SWITCH_SYSTEM".equals(action) || action.startsWith("RLGL_");','return "SWITCH_SYSTEM".equals(action) || action.startsWith("RLGL_") || "SKY_SQUID_START".equals(action);');f.write_text(s)
f=Path('checks/GameSystemsCheck.java');s=f.read_text().replace('check(sky.press(0,roster,100)&&sky.initialCount()==4,"First button freezes roster");','sky.begin(roster);\n        check(sky.press(0,roster,100)&&sky.initialCount()==4,"Activation roster starts at first button");').replace('sky.press(0,roster,0);','sky.begin(roster);sky.press(0,roster,0);');s=s.replace('        var box=new MpsqGameZone', '''        sky.fall(Set.of(s2));check(sky.tower()==1&&!sky.survivors().contains(s2),"Y fall during green transit remains fatal without skipping tower");
        check(sky.press(1,Set.of(s3,s4),1),"Reduced surviving roster can start next tower");
        sky.fall(Set.of(s3,s4));check(sky.tower()==3&&sky.survivors().isEmpty(),"Simultaneous falls eliminate everyone");
        sky.begin(roster);check(!sky.press(0,Set.of(s1,s2,s3,late),0),"Late entrant cannot replace missing activation participant");
        sky.press(0,roster,0);sky.tick(Set.of(s1),1);sky.tick(Set.of(s2,s3,s4),2);
        check(sky.survivors().equals(Set.of(s2,s3,s4)),"Green transit outside previous tower is safe");
        sky.press(1,Set.of(s2,s3,s4),3);sky.tick(Set.of(s2),4);sky.press(2,Set.of(s3,s4),5);sky.tick(Set.of(s3),6);
        sky.fall(Set.of(s4));check(sky.survivors().isEmpty(),"Y fall is fatal even after third tower passed");
        var box=new MpsqGameZone''');f.write_text(s)
