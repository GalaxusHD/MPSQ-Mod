package de.galaxushd.mpsqcamera;

import net.minecraft.util.Identifier;

import java.util.List;

/** Built-in Mini You and animal pets with their display metadata. */
public final class MpsqPetCatalog {
    public enum Group { MINI_YOU, TEAM_ROLE, ANIMAL }

    public record Pet(String id, String name, String texture, Group group,
                      TeamRank issuerRole, String interactionSound, String soundNotes,
                      String miniModel) {
        public Pet(String id,String name,String texture,Group group,TeamRank issuerRole,String interactionSound,String soundNotes){this(id,name,texture,group,issuerRole,interactionSound,soundNotes,null);}
        public int textureHeight() { return texture.equals("der_wut_knut") || texture.startsWith("budgie_") ? 32 : 64; }
        public Identifier textureId() {
            return Identifier.of(MpsqCameraClient.MOD_ID, texture.startsWith("budgie_")
                    ? "textures/pets/budgies/" + texture.substring("budgie_".length()) + ".png"
                    : "textures/pets/" + texture + ".png");
        }
    }

    private static final List<Pet> PETS = List.of(
            mini("my_skin", "Mein Skin", "__player__", null, "Verwendet deinen aktuell geladenen Minecraft-Skin (Slim oder Normal)."),
            miniModel("nm_mini_you_player", "Mein Mini-You", "__player__", "nm_mini_you_1", null,
                    "Verwendet deinen aktuellen Minecraft-Skin. Slim-Skin-Modelle können separat ergänzt werden."),
            mini("seong_gi_hun", "Seong Gi-hun", "gi_hun", "ive-played-these-games-before_Q9d8uER.mp3", "Eigener Interaktionssound; sonst allgemeine Mini-You-Laufgeräusche."),
            mini("monty", "Monty", "monty", "20221030_CrunchyUglyToadKeyboardCat-L-oRCpTmjqu1QlN__source.mp4", "Eigener Interaktionssound; sonst allgemeine Mini-You-Laufgeräusche."),
            mini("der_pandi", "der_pandi", "der_pandi", null, "Keine eigenen Sounds."),
            mini("harryron", "Harryron_", "harryron", null, "Keine eigenen Sounds."),
            mini("hecamus", "Hecamus", "hecamus", null, "Keine eigenen Sounds."),
            mini("der_wut_knut", "DerWutKnut", "der_wut_knut", null, "Keine eigenen Sounds."),
            mini("de_merzli", "DeMerzli", "de_merzli", null, "Keine eigenen Sounds."),
            mini("mr_creeper", "Mr_Creeper", "mr_creeper", null, "Keine eigenen Sounds."),
            mini("agent_chicken_789", "AgentChicken789", "agent_chicken_789", null, "Keine eigenen Sounds."),
            mini("endermansdl", "endermansdl", "endermansdl", null, "Keine eigenen Sounds."),
            mini("nm_mini_you_1", "Mini You 1", "mini_you_neutral", null, "Streift umher und winkt bei Interaktion; Testvariante ohne den ursprünglichen festen Spielerskin."),
            mini("nm_mini_you_2", "Mini You 2", "mini_you_neutral", null, "Streift umher und winkt bei Interaktion; Testvariante ohne den ursprünglichen festen Spielerskin."),
            mini("nm_mini_you_3", "Mini You 3", "mini_you_neutral", null, "Streift umher und winkt bei Interaktion; Testvariante ohne den ursprünglichen festen Spielerskin."),
            new Pet("nogs_budgie", "Nog's Wellensittich", "budgie_green", Group.ANIMAL, null,
                    null, "Fliegt frei herum, erkundet die Umgebung und reagiert auf Spieler."),
            new Pet("nocsy_otter", "Nocsy-Otter", "nocsy_otter_v2", Group.ANIMAL, null,
                    null, "Läuft frei herum, schwimmt im Wasser und lässt sich mit Rechtsklick streicheln."),
            new Pet("nogs_hedgehog", "Nog's Igel", "nogs_hedgehog", Group.ANIMAL, null,
                    null, "Erkundet die Umgebung, schnüffelt bei Rechtsklick und stößt sich an Hindernissen ab."),
            role("kreis", "Kreis", "kreis", TeamRank.WORKER),
            role("soldat", "Soldat", "soldat", TeamRank.SOLDIER),
            role("offizier", "Offizier", "offizier", TeamRank.OFFICER),
            role("sr_offizier", "Sr. Offizier", "sr_offizier", TeamRank.SENIOR_OFFICER),
            role("frontman", "Frontman", "frontman", TeamRank.FRONTMAN)
    );

    private MpsqPetCatalog() { }

    private static Pet mini(String id, String name, String texture, String sound, String notes) {
        String model=id.matches("nm_mini_you_[1-3]")?id:null;
        return new Pet(id, name, texture, Group.MINI_YOU, null, sound, notes, model);
    }

    private static Pet miniModel(String id,String name,String texture,String model,String sound,String notes){
        return new Pet(id,name,texture,Group.MINI_YOU,null,sound,notes,model);
    }

    private static Pet role(String id, String name, String texture, TeamRank rank) {
        return new Pet(id, name, texture, Group.TEAM_ROLE, rank, null,
                "Keine eigenen Sounds. Ausgabe nur durch " + rank.label() + ".");
    }

    public static boolean isAnimal(Pet pet) { return pet != null && pet.group() == Group.ANIMAL; }

    public static List<Pet> all() { return PETS; }
    public static List<Pet> group(Group group) { return PETS.stream().filter(p -> p.group() == group).toList(); }
    public static Pet byId(String id) { return PETS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null); }
}

