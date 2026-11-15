package de.galaxushd.mpsqcamera;

import java.util.List;
import net.minecraft.resources.Identifier;

/** Built-in Mini You and animal pets with their display metadata. */
public final class MpsqPetCatalog {
    public enum Group { MINI_YOU, TEAM_ROLE, ANIMAL }

    public record Pet(String id, String name, String texture, Group group,
                      TeamRank issuerRole, String interactionSound, String soundNotes,
                      String miniModel) {
        public Pet(String id,String name,String texture,Group group,TeamRank issuerRole,String interactionSound,String soundNotes){this(id,name,texture,group,issuerRole,interactionSound,soundNotes,null);}
        public int textureHeight() { return texture.equals("der_wut_knut") || texture.startsWith("budgie_") ? 32 : 64; }
        public Identifier textureId() {
            return Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, texture.startsWith("budgie_")
                    ? "textures/pets/budgies/" + texture.substring("budgie_".length()) + ".png"
                    : "textures/pets/" + texture + ".png");
        }
    }

    private static final List<Pet> PETS = List.of(
            mini("monty", "_Monty_", "monty", "20221030_CrunchyUglyToadKeyboardCat-L-oRCpTmjqu1QlN__source.mp4", "Eigener Interaktionssound; sonst allgemeine Mini-You-Laufgeräusche."),
            mini("hecamus", "Hecamus", "hecamus", null, "Keine eigenen Sounds."),
            mini("harryron", "Harryron_", "harryron", null, "Keine eigenen Sounds."),
            mini("der_pandi", "der_pandi", "der_pandi", null, "Keine eigenen Sounds."),
            mini("de_merzli", "DeMerzli", "de_merzli", null, "Keine eigenen Sounds."),
            mini("der_wut_knut", "Der WutKnut", "der_wut_knut", null, "Keine eigenen Sounds."),
            miniModel("endermansdl", "endermansdl", "endermansdl", "nm_mini_you_teleport", null, "Rechtsklick: Winken oder Teleport (50/50)."),
            mini("agent_chicken_789", "Agentchicken", "agent_chicken_789", null, "Keine eigenen Sounds."),
            mini("mr_creeper", "Mr_Creeper", "mr_creeper", null, "Keine eigenen Sounds."),
            mini("my_skin", "Eigener Skin", "__player__", null, "Verwendet deinen aktuellen Minecraft-Skin; Slim- und normale Skins werden erkannt."),
            mini("seong_gi_hun", "Seong Gi-hun / Spieler 456", "gi_hun", "ive-played-these-games-before_Q9d8uER.mp3", "Eigener Interaktionssound; sonst allgemeine Mini-You-Laufgeräusche."),
            staffMini("kreis_minime", "Arbeiter", "kreis"),
            staffMini("soldat_minime", "Soldat", "soldat"),
            staffMini("offizier_minime", "Offizier", "offizier"),
            staffMini("sr_offizier_minime", "Sr_Offizier", "sr_offizier"),
            staffMini("frontman_minime", "Frontmann", "frontman"),
            new Pet("nogs_budgie", "Nog's Wellensittich", "budgie_green", Group.ANIMAL, null,
                    null, "Fliegt frei herum, erkundet die Umgebung und reagiert auf Spieler."),
            new Pet("nocsy_otter", "Nocsy-Otter", "nocsy_otter_v2", Group.ANIMAL, null,
                    null, "Läuft frei herum, schwimmt im Wasser und lässt sich mit Rechtsklick streicheln."),
            new Pet("mpsq_crab", "Krabbe", "mpsq_crab", Group.ANIMAL, null, null, "Läuft mit Idle- und Walkanimation."),
            new Pet("mpsq_penguin", "Pinguin", "mpsq_penguin", Group.ANIMAL, null, null, "Läuft mit Idle- und Walkanimation."),
            new Pet("mpsq_baby_happy_ghast", "Baby Happy Ghast", "mpsq_baby_happy_ghast", Group.ANIMAL, null, null, "Schwebt mit Idle- und Walkanimation."),
            new Pet("mpsq_colin_the_creeper", "Colin der Creeper", "mpsq_colin_the_creeper", Group.ANIMAL, null, null, "Läuft mit Idle- und Walkanimation; Rechtsklick löst Fuse aus."),
            new Pet("mpsq_exo_the_enderman", "Exo der Enderman", "mpsq_exo_the_enderman", Group.ANIMAL, null, null, "Läuft mit Idle- und Walkanimation; Rechtsklick löst Teleport aus."),
            new Pet("mpsq_slak_the_skeleton", "Slak das Skelett", "mpsq_slak_the_skeleton", Group.ANIMAL, null, null, "Läuft mit Idle- und Walkanimation; Rechtsklick lässt den Kopf kreisen."),
            new Pet("mpsq_zekk_the_zombie", "Zekk der Zombie", "mpsq_zekk_the_zombie", Group.ANIMAL, null, null, "Läuft mit Idle- und Walkanimation; Rechtsklick löst Trip aus."),
            new Pet("nogs_hedgehog", "Nog's Igel", "nogs_hedgehog", Group.ANIMAL, null,
                    null, "Erkundet die Umgebung, schnüffelt bei Rechtsklick und stößt sich an Hindernissen ab."),
            new Pet("nogs_owl_barn", "Schleiereule", "nm_owl_barn", Group.ANIMAL, null, null, "Erkundet die Umgebung; Rechtsklick spielt die Interaktion ab."),
            new Pet("nogs_owl_horned", "Virginia-Uhu", "nm_owl_horned", Group.ANIMAL, null, null, "Erkundet die Umgebung; Rechtsklick spielt die Interaktion ab."),
            new Pet("nogs_owl_long_eared", "Waldohreule", "nm_owl_long_eared", Group.ANIMAL, null, null, "Erkundet die Umgebung; Rechtsklick spielt die Interaktion ab."),
            new Pet("nogs_owl_snowy", "Schneeeule", "nm_owl_snowy", Group.ANIMAL, null, null, "Erkundet die Umgebung; Rechtsklick spielt die Interaktion ab."),
            new Pet("mpsq_tiger", "Tiger", "nm_tiger", Group.ANIMAL, null, null, "Erwachsener Tiger; Variante: Baby-Tiger."),
            new Pet("mpsq_tiger_baby", "Baby-Tiger", "nm_tiger_baby", Group.ANIMAL, null, null, "Baby-Tiger; Variante: erwachsener Tiger."),
            role("kreis", "Kreis", "kreis", TeamRank.WORKER),
            role("soldat", "Soldat", "soldat", TeamRank.SOLDIER),
            role("offizier", "Offizier", "offizier", TeamRank.OFFICER),
            role("sr_offizier", "Sr. Offizier", "sr_offizier", TeamRank.SENIOR_OFFICER),
            role("frontman", "Frontman", "frontman", TeamRank.FRONTMAN)
    );

    private MpsqPetCatalog() { }

    private static Pet mini(String id, String name, String texture, String sound, String notes) {
        return new Pet(id, name, texture, Group.MINI_YOU, null, sound, notes, "nm_mini_you_1");
    }

    private static Pet staffMini(String id, String name, String texture) {
        return new Pet(id, name, texture, Group.MINI_YOU, TeamRank.OFFICER, null,
                "Squid-Game-Team-Skin; ab Offizier verfügbar.", "nm_mini_you_1");
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

