from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera')
f=p/'MpsqGamesRuntime.java';s=f.read_text().replace('private static final Set<String> excludedNames = new HashSet<>();','private static final Set<String> excludedNames = new HashSet<>();\n    private static final Set<net.minecraft.core.BlockPos> skyPowered = new HashSet<>();').replace('skyRound.reset();MpsqBossbarManager','skyRound.reset();skyPowered.clear();MpsqBossbarManager')
s=s.replace('    // Block updates alone must not start a tower: only configured MPSQ actions do.\n    public static void skyButtonUpdate(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {}', '''    // Observe other players pressing configured tower buttons as well.
    public static void skyButtonUpdate(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if(!mode.equals("sky_squid"))return;
        boolean powered=state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock
                &&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
        if(!powered){skyPowered.remove(pos);return;}
        if(skyPowered.add(pos.immutable())&&MpsqTriggerManager.isSkyTowerButton(pos))startSkyTower(pos);
    }''');f.write_text(s)
f=p/'MpsqTriggerManager.java';s=f.read_text().replace('    private static boolean isButtonAction(String action) {','''    static boolean isSkyTowerButton(BlockPos pos) {
        var client=Minecraft.getInstance();
        if(client.level==null)return false;
        String blockId=BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString();
        if(MpsqActionSync.server().isBlank()&&client.getSingleplayerServer()!=null){
            JsonObject row=MpsqLocalActionStore.find(pos);
            return row!=null&&blockId.equals(row.get("blockId").getAsString())&&"SKY_SQUID_START".equals(row.get("actionType").getAsString());
        }
        return TRIGGERS.stream().anyMatch(t->t.position().equals(pos)&&t.worldId().equals(MpsqActionSync.world())&&"SKY_SQUID_START".equals(t.actionType()));
    }
    private static boolean isButtonAction(String action) {''');f.write_text(s)
f=p/'MpsqActionSetupScreen.java';s=f.read_text().replace('body.addProperty("minimumRank","OPEN_LINK"','body.addProperty("minimumRank","SKY_SQUID_START".equals(actions[action])?"spieler":"OPEN_LINK"');f.write_text(s)
f=p/'MpsqActionSync.java';s=f.read_text().replace('if(!data.has("height")||!Double.isFinite(data.get("height").getAsDouble()))return;','''if(!data.has("height")||!Double.isFinite(data.get("height").getAsDouble())){
                        var player=Minecraft.getInstance().player;
                        if(player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player,Component.literal("Bitte diesen System-Knopf mit /mpsq-redstone neu einrichten und die Y-Grenze speichern."),false);
                        return;
                    }''');f.write_text(s)
