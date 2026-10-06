package de.galaxushd.mpsqcamera;

import net.minecraft.util.Identifier;

import java.util.List;

/** Built-in Mini You presets and their display metadata. */
public final class MpsqPetCatalog {
    public enum Group { MINI_YOU, TEAM_ROLE }

    public record Pet(String id, String name, String texture, Group group,
                      TeamRank issuerRole, String interactionSound, String soundNotes) {
        public int textureHeight() { return texture.equals("der_wut_knut") ? 32 : 64; }
        public Identifier textureId() {
            return Identifier.of(MpsqCameraClient.MOD_ID, "textures/pets/" + texture + ".png");
        }
    }

    private static final List<Pet> PETS = List.of(
            mini("my_skin", "Mein Skin", "__player__", null, "Verwendet deinen aktuell geladenen Minecraft-Skin (Slim oder Normal)."),
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
            role("kreis", "Kreis", "kreis", TeamRank.WORKER),
            role("soldat", "Soldat", "soldat", TeamRank.SOLDIER),
            role("offizier", "Offizier", "offizier", TeamRank.OFFICER),
            role("sr_offizier", "Sr. Offizier", "sr_offizier", TeamRank.SENIOR_OFFICER),
            role("frontman", "Frontman", "frontman", TeamRank.FRONTMAN)
    );

    private MpsqPetCatalog() { }

    private static Pet mini(String id, String name, String texture, String sound, String notes) {
        return new Pet(id, name, texture, Group.MINI_YOU, null, sound, notes);
    }

    private static Pet role(String id, String name, String texture, TeamRank rank) {
        return new Pet(id, name, texture, Group.TEAM_ROLE, rank, null,
                "Keine eigenen Sounds. Ausgabe nur durch " + rank.label() + ".");
    }

    public static List<Pet> all() { return PETS; }
    public static List<Pet> group(Group group) { return PETS.stream().filter(p -> p.group() == group).toList(); }
    public static Pet byId(String id) { return PETS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null); }
}
