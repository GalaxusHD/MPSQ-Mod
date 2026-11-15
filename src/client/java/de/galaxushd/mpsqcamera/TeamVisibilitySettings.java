package de.galaxushd.mpsqcamera;

/** Persistent local switch for optional MPSQ client features. */
public final class TeamVisibilitySettings {
    private TeamVisibilitySettings() { }
    public static boolean visible() { return ModConfig.mpsqEnabled; }
    /** Scoped features depend on the real local player, never the detached camera. */
    public static boolean inArea(){return visible()&&MpsqZoneCatalog.inMaster(net.minecraft.client.Minecraft.getInstance().player);}
    public static void requirePlacement(double x,double y,double z){
        if(!visible())throw new IllegalArgumentException("MPSQ ist deaktiviert.");
        if(!MpsqZoneCatalog.inMaster(x,y,z))throw new IllegalArgumentException("Platzierung nur innerhalb der Gesamtfläche möglich.");
    }
    public static void requireScreenPlacement(LocalScreenStore.ScreenInputType type,net.minecraft.core.BlockPos first,net.minecraft.core.BlockPos second){
        if(!visible())throw new IllegalArgumentException("MPSQ ist deaktiviert.");
        if(type!=LocalScreenStore.ScreenInputType.LINK&&!MpsqZoneCatalog.inMaster(new MpsqGameZone(first,second)))
            throw new IllegalArgumentException("Die gesamte Platzierung muss innerhalb der Gesamtfläche liegen.");
    }
    public static void toggle() {
        ModConfig.mpsqEnabled = !ModConfig.mpsqEnabled;
        ModConfig.save();
    }
}
