import de.galaxushd.mpsqcamera.*;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public final class TeamPresentationTest {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    private static TeamProfile profile(String name, TeamRank rank, boolean visible) {
        return new TeamProfile(UUID.randomUUID(), name, rank, null, visible);
    }
    public static void main(String[] args) {
        for (TeamRank temporary : TeamRank.values()) {
            TeamProfile senior = new TeamProfile(UUID.randomUUID(), "MP_SquidGame", TeamRank.SENIOR_OFFICER, temporary, true);
            check(senior.displayedRank() == temporary, "temporary display lost");
            check(senior.permissionRank() == TeamRank.SENIOR_OFFICER, "root permissions lost for " + temporary);
            check(senior.canOpenTeamArea() && senior.canViewCameras() && senior.canUseTexts(), "root tool locked for " + temporary);
            check(senior.canManageMember(profile("Other", TeamRank.FRONTMAN, true)), "root cannot manage frontman");
            check(!senior.canManageMember(senior), "permanent root can be demoted");
            check(!profile("Other", TeamRank.OFFICER, true).canManageMember(senior), "temporary rank exposes root to demotion");
        }
        TeamProfile officerEvent = new TeamProfile(UUID.randomUUID(), "Officer", TeamRank.OFFICER, TeamRank.UNDERCOVER_001, true);
        check(officerEvent.canUseTexts(), "client texts permission differs from backend");
        check(officerEvent.permissionRank() == TeamRank.UNDERCOVER_001, "ordinary event restrictions removed");
        TeamProfile root = profile("MP_SquidGame", TeamRank.SENIOR_OFFICER, true);
        TeamProfile duplicate = profile("mp_squidgame", TeamRank.PLAYER, true);
        for (List<TeamProfile> rows : List.of(List.of(root, duplicate), List.of(duplicate, root))) {
            TeamStateStore.setMembers(rows);
            check(TeamStateStore.members().size() == 1, "duplicate remains");
            check(TeamStateStore.byMinecraftName("MP_SQUIDGAME").orElseThrow().id().equals(root.id()), "root lost");
        }
        TeamStateStore.setSelf(duplicate);
        check(TeamStateStore.self().orElseThrow().permissionRank() == TeamRank.PLAYER, "duplicate granted root privileges");
        check(TeamStateStore.members().size() == 1, "setSelf reintroduced duplicate");
        TeamProfile target = profile("Name_Test", TeamRank.STREAMER, true);
        TeamStateStore.setMembers(List.of(target));
        TeamStateStore.setSelf(target);
        TeamStateStore.setSelf(new TeamProfile(target.id(), target.displayName(), target.baseRank(), null, false));
        check(!TeamStateStore.byMinecraftName("name_test").orElseThrow().nameVisible(), "self update missed member cache");
        TeamStateStore.setSelf(profile("Viewer", TeamRank.PLAYER, true));
        check(MpsqNametags.forPlayer("Name_Test").getString().equals("\ue009"), "hidden name leaked");
        TeamStateStore.setSelf(profile("Officer", TeamRank.OFFICER, true));
        check(MpsqNametags.forPlayer("Name_Test").getString().contains("Name_Test"), "officer visibility changed");
        TeamStateStore.setSelf(null);
        check(MpsqNametags.forPlayer("Unregistered").getString().equals("\ue002 Unregistered"), "missing fallback rank");
        Text text = MpsqNametags.forPlayer("Unregistered");
        text.visit((style, content) -> {
            if (content.contains("\ue002")) check(style.getFont().toString().equals("mpsqcamera:ranks"), "server font used for rank");
            if (content.contains("Unregistered")) check(style.getFont().toString().equals("minecraft:default"), "bitmap font used for name");
            return Optional.empty();
        }, Style.EMPTY);
        check(TeamStateStore.byMinecraftName("Name").isEmpty(), "partial account name matched");
        System.out.println("Team presentation regressions passed");
    }
}
