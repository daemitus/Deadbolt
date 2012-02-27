
import com.daemitus.deadbolt.Deadbolt;
import com.daemitus.deadbolt.Deadbolted;
import com.daemitus.deadbolt.listener.DeadboltListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.data.Group;
import org.anjocaido.groupmanager.dataholder.OverloadedWorldHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public final class EssentialsGroupManagerListener extends DeadboltListener {

    private static final String patternBracketTooLong = "\\[.{14,}\\]";
    private GroupManager gm;

    @Override
    public List<String> getDependencies() {
        return Arrays.asList("GroupManager");
    }

    @Override
    public void load(final Deadbolt plugin) {
        gm = (GroupManager) plugin.getServer().getPluginManager().getPlugin("GroupManager");
    }

    @Override
    public boolean canPlayerInteract(Deadbolted db, PlayerInteractEvent event) {
        Player player = event.getPlayer();
        OverloadedWorldHolder owh = gm.getWorldsHolder().getWorldData(player);
        Group group = owh.getUser(player.getName()).getGroup();
        Set<String> groupNames = new HashSet<String>();
        groupNames.add(group.getName());
        getInherited(group, groupNames, owh);

        for (String gName : groupNames) {
            if (db.getUsers().contains(truncate("[" + gName + "]")))
                return true;
        }
        
        return false;
    }

    private void getInherited(Group group, Set<String> groupNames, OverloadedWorldHolder owh) {
        for (String gs : group.getInherits()) {
            if (groupNames.add(gs)) {
                getInherited(owh.getGroup(gs), groupNames, owh);
            }
        }
    }

    private String truncate(String text) {
        if (text.matches(patternBracketTooLong))
            return "[" + text.substring(1, 14) + "]";
        return text;
    }
}
