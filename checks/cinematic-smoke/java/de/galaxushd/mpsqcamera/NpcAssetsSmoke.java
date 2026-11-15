package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

final class NpcAssetsSmoke {
    private static MpsqScenes.Target target;
    private static MpsqCinematicScene ambient;
    static void run(ClientGameTestContext ctx){
        ctx.runOnClient(c->{
            var glowNpc=new MpsqCinematicScene.Npc();if(glowNpc.glow)throw new AssertionError("Glow default must be off");
            String glowId="glow-regression";glowNpc.glow=true;
            String[] roles={"none","quest","accessories","tutorial"};int[] colors={0xFFEC2F53,0xFF2149C4,0xFF8027B0,0xFFC3971F};
            for(int i=0;i<roles.length;i++){glowNpc.scenesTask=roles[i];if(MpsqNpcGlow.color(glowNpc)!=colors[i])throw new AssertionError("Custom palette");}
            glowNpc.scenesTask="none";glowNpc.type=MpsqAccessoryRenderer.WUMPUS_ASSET_ID;if(MpsqNpcGlow.color(glowNpc)!=0xFF7582E3)throw new AssertionError("Discord palette");
            MpsqNpcGlow.rearm(glowId);if(!MpsqNpcGlow.enabled(glowId,glowNpc))throw new AssertionError("Enable glow");MpsqNpcGlow.clicked(glowId);if(MpsqNpcGlow.enabled(glowId,glowNpc))throw new AssertionError("Click must dismiss glow");MpsqNpcGlow.rearm(glowId);if(!MpsqNpcGlow.enabled(glowId,glowNpc))throw new AssertionError("Rearm glow");
            glowNpc.category="mob";glowNpc.type="minecraft:chicken";glowNpc.scenesTask="quest";var glowEntity=MpsqCinematicActors.create(glowNpc);MpsqNpcGlow.apply(glowEntity,glowId,glowNpc);if(glowEntity.getTeamColor()!=0xFF2149C4)throw new AssertionError("Native entity custom glow color");
            glowNpc.rankTag="frontman";if(!MpsqNpcGlow.tag(glowNpc).equals("quests"))throw new AssertionError("Task tag not locked");
            glowEntity.setId(-1899999000);var eye=c.gameRenderer.mainCamera().position();glowEntity.setPos(eye.x+3,eye.y-.3,eye.z);MpsqNpcGlow.apply(glowEntity,glowId,glowNpc);
            if(!c.shouldEntityAppearGlowing(glowEntity))throw new AssertionError("Glow switch does not enable native outline independently");
            var wall=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++){var b=net.minecraft.core.BlockPos.containing(eye.x+1,eye.y+y,eye.z+z);wall.put(b,c.level.getBlockState(b));c.level.setBlock(b,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);}
            if(c.shouldEntityAppearGlowing(glowEntity))throw new AssertionError("NPC glow visible through wall");wall.forEach((b,state)->c.level.setBlock(b,state,3));
            if(MpsqMovementSensorSystem.shouldOutlineEntity(glowEntity)||MpsqGamesRuntime.outline(glowEntity))throw new AssertionError("NPC participates in game outlines");
            glowNpc.category="npc_skin_normal";glowNpc.type="self";glowNpc.name="Test";glowNpc.scenesTask="quest";if(!MpsqNpcLabels.name(glowNpc).isBlank())throw new AssertionError("Task and name stack");glowNpc.scenesTask="none";if(!MpsqNpcLabels.name(glowNpc).equals("Test"))throw new AssertionError("Custom name missing");if(Math.abs(MpsqNpcLabels.height(glowNpc,2)-4.1)>.001)throw new AssertionError("Scaled label height");
            var nativePlayer=MpsqCinematicActors.create(glowNpc);nativePlayer.setId(-1899999001);if(MpsqGamesRuntime.participant((net.minecraft.world.entity.player.Player)nativePlayer))throw new AssertionError("NPC is game participant");
            if(LocalMpsqBackend.withBuiltinAccessories(new com.google.gson.JsonArray()).size()!=5)throw new AssertionError("Incomplete bundled accessory catalog");
            System.out.println("NPC_CUSTOM_GLOW_PASS");
            for(String id:MpsqBuiltinMusic.IDS){if(c.getSoundManager().getSoundEvent(net.minecraft.resources.Identifier.parse(MpsqBuiltinMusic.sound(id)))==null)throw new AssertionError("Bundled music event missing: "+id);}
            if(MpsqBuiltinMusic.sound("01")!=null||MpsqBuiltinMusic.sound("02")!=null||MpsqBuiltinMusic.sound("03")!=null)throw new AssertionError("Excluded music included");
            var catalog=MpsqBuiltinNpcCatalog.catalog();
            for(String id:MpsqBuiltinNpcCatalog.SKINS){boolean found=false;for(var e:catalog){var row=e.getAsJsonObject();if(id.equals(MpsqScenes.str(row,"id",""))){found=true;String url=MpsqScenes.str(row,"url","");MpsqAccessoryRenderer.prepareCinematicSkin(url,false);}}
                if(!found)throw new AssertionError("Bundled skin missing: "+id);
            }
            long owls=MpsqPetCatalog.all().stream().filter(p->p.id().startsWith("nogs_owl_")).count();if(owls!=4)throw new AssertionError("Four owls expected");
            for(var p:MpsqPetCatalog.all())if(p.id().startsWith("nogs_owl_")){MpsqOwlModel.select(p.id());if(MpsqOwlModel.animationLength("walk")<=0)throw new AssertionError("Owl animation missing");}
            var hats=LocalMpsqBackend.accessoryCatalog();for(String id:new String[]{"nm_hat_seagull","nm_hat_owl_long_eared"}){boolean found=false;for(var e:hats)if(("builtin://mpsq/"+id).equals(MpsqAccessoryRenderer.resolveBuiltinAccessory(e.getAsJsonObject())))found=true;if(!found)throw new AssertionError("Hat missing: "+id);}
            MpsqAccessoryRenderer.cinematicClips("builtin://mpsq/nm_seagull");
        });
        ctx.setScreen(()->new MpsqMusicPlayerScreen());ctx.waitTicks(3);ctx.takeScreenshot("restored-specials-playlist");
        ctx.setScreen(()->new MpsqPetMenuScreen(null,MpsqPetMenuScreen.PetKind.ANIMALS));ctx.waitTicks(30);ctx.takeScreenshot("new-owl-variants");
        ctx.runOnClient(c->{
            if(!MpsqAccessoryRenderer.cinematicClips("builtin://mpsq/nm_seagull").contains("walk"))throw new AssertionError("Seagull flight clip missing");
            for(String id:MpsqBuiltinNpcCatalog.SKINS)if(MpsqNpcSkinRenderer.get("builtin://mpsq/skin/"+id.toLowerCase(java.util.Locale.ROOT),false)==null)throw new AssertionError("Skin did not load: "+id);
            c.gui.setScreen(null);target=MpsqScenes.create();var p=MpsqScenes.copy(target.profile());p.npc.category="mob";p.npc.type="minecraft:villager";p.npc.url="";p.npc.z+=3;p.visibility="ALL";MpsqScenes.save(target,p);MpsqAccessoryRenderer.refresh();
        });ctx.waitTicks(30);
        ctx.runOnClient(c->{if(!mobActors().containsKey(target.key().substring(4)))throw new AssertionError("Created villager invisible");var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.visibility="CINEMATIC";MpsqScenes.save(target,p);});ctx.waitTicks(3);
        ctx.runOnClient(c->{if(mobActors().containsKey(target.key().substring(4)))throw new AssertionError("Cinematic-only NPC visible without playback");
            var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.visibility="ALL";p.npc.category="npc_skin_normal";p.npc.type="self";p.npc.url="self";p.npc.equipment[4]="minecraft:diamond_sword";MpsqScenes.save(target,p);
        });ctx.waitTicks(3);ctx.takeScreenshot("unified-npc-player-equipment");
        ctx.runOnClient(c->{if(!mobActors().containsKey(target.key().substring(4)))throw new AssertionError("Player NPC invisible");
            ambient=new MpsqCinematicScene();ambient.dimension=c.level.dimension().identifier().toString();ambient.npc.placed=true;ambient.npc.visibility="ALL";ambient.npc.x=c.player.getX()+2;ambient.npc.y=c.player.getY();ambient.npc.z=c.player.getZ()+3;
            try{MpsqCinematicStore.save(java.util.List.of(ambient));}catch(Exception e){throw new AssertionError(e);}
        });ctx.waitTicks(3);
        ctx.runOnClient(c->{if(!mobActors().containsKey("cinematic:"+ambient.id))throw new AssertionError("All-visible cinematic NPC missing");
            ambient.npc.visibility="CINEMATIC";try{MpsqCinematicStore.save(java.util.List.of(ambient));}catch(Exception e){throw new AssertionError(e);}
        });ctx.waitTicks(3);
        ctx.runOnClient(c->{var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.npc.category="mob";p.npc.type="minecraft:chicken";p.npc.url="";p.npc.name="Moving chicken";p.npc.x=c.player.getX();p.npc.y=c.player.getY();p.npc.z=c.player.getZ()+3;p.npc.movementStart=MpsqScenes.time();p.loop=false;p.enabled=true;p.npc.route[0]=new MpsqCinematicScene.Waypoint(p.npc.x,p.npc.y,p.npc.z,1);p.npc.route[1]=new MpsqCinematicScene.Waypoint(p.npc.x+40,p.npc.y,p.npc.z,4);for(int dx=0;dx<=41;dx++)c.level.setBlock(net.minecraft.core.BlockPos.containing(p.npc.x+dx,p.npc.y-.1,p.npc.z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);MpsqScenes.save(target,p);});
        ctx.waitTicks(50);
        ctx.runOnClient(c->{var actor=mobActors().get(target.key().substring(4));var entity=(net.minecraft.world.entity.LivingEntity)QuestGuiSmoke.get(actor,"entity");var p=MpsqScenes.stored(target.key().substring(4));if(entity.getX()<p.npc.x+5)throw new AssertionError("Actor did not follow route");if(Math.abs(entity.getBoundingBox().getCenter().x-entity.getX())>.01)throw new AssertionError("Culling bounds stayed at creation point");if(!entity.onGround())throw new AssertionError("Walking chicken not grounded");var chicken=(net.minecraft.world.entity.animal.chicken.Chicken)entity;if(chicken.flapSpeed>.01)throw new AssertionError("Grounded chicken flapping");var frustum=new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(),new org.joml.Matrix4f().ortho(-256,256,-256,256,-256,256));frustum.prepare(entity.getX(),entity.getY(),entity.getZ());var renderer=c.getEntityRenderDispatcher().getRenderer(entity);if(!renderer.shouldRender(entity,frustum,entity.getX()-64,entity.getY(),entity.getZ(),1))throw new AssertionError("NPC culled at 64 blocks");if(renderer.shouldRender(entity,frustum,entity.getX()-129,entity.getY(),entity.getZ(),1))throw new AssertionError("NPC culling range not bounded");var current=entity.position();MpsqNpcMotion.place(entity,new MpsqCinematicActors.Pose(current.x+.1,current.y,current.z,180,0,0,"idle",0),true);if(Math.abs(entity.getPosition(.5f).x-(current.x+.05))>.001)throw new AssertionError("NPC interpolation snaps");});
        System.out.println("NPC_MOTION_CULLING_GROUND_PASS");
        ctx.runOnClient(c->{var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.npc.category="npc_skin_normal";p.npc.type="self";p.npc.url="self";p.npc.name="Testname";p.npc.rankTag=TeamRank.OFFICER.id();p.task="none";p.npc.scenesTask="none";p.npc.route=new MpsqCinematicScene.Waypoint[21];p.enabled=false;p.npc.x=c.player.getX();p.npc.y=c.player.getY();p.npc.z=c.player.getZ()-3;p.npc.yaw=0;p.npc.equipment[0]="mpsqcamera:nm_hat_budgie";p.npc.glow=true;MpsqNpcGlow.rearm(target.key());MpsqScenes.save(target,p);c.player.setYRot(180);c.player.setXRot(0);});ctx.waitTicks(10);ctx.takeScreenshot("npc-inline-rank-name-budgie-hat");
        ctx.runOnClient(c->{var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.task="tutorial";MpsqScenes.save(target,p);});ctx.waitTicks(3);ctx.takeScreenshot("npc-single-task-tag");
        ctx.runOnClient(c->{var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.task="none";p.npc.rankTag=TeamRank.OFFICER.id();p.clip=MpsqScenesClips.all().get(0).id();MpsqScenes.save(target,p);});ctx.waitTicks(20);ctx.takeScreenshot("npc-animation-retains-label");
        ctx.setScreen(()->new MpsqOwnedAccessoriesScreen(null));ctx.waitTicks(25);ctx.takeScreenshot("all-five-bundled-hats");ctx.setScreen(()->null);

        ctx.runOnClient(c->{if(mobActors().containsKey("cinematic:"+ambient.id))throw new AssertionError("Cinematic-only ambient NPC visible");var p=MpsqScenes.copy(MpsqScenes.stored(target.key().substring(4)));p.deleted=true;MpsqScenes.save(target,p);});
        System.out.println("NPC_ASSETS_SMOKE_PASS");
    }
    private static java.util.Map<?,?> mobActors(){try{var f=MpsqSceneWorldNpcs.class.getDeclaredField("mobs");f.setAccessible(true);return (java.util.Map<?,?>)f.get(null);}catch(Exception e){throw new AssertionError(e);}}
}

