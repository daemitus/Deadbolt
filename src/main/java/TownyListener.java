
import com.daemitus.deadbolt.listener.DeadboltListener;
import com.daemitus.deadbolt.Config;
import com.daemitus.deadbolt.Deadbolt;
import com.daemitus.deadbolt.Deadbolted;
import com.palmergames.bukkit.towny.NotRegisteredException;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyUniverse;
import com.palmergames.bukkit.towny.object.TownyWorld;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class TownyListener extends DeadboltListener {

    private TownyUniverse towny;
    private static final String patternBracketTooLong = "\\[.{14,}\\]";
    private static boolean denyWilderness = false;
    private static boolean wildernessOverride = false;
    private static boolean mayorOverride = false;
    private static boolean assistantOverride = false;

    @Override
    public List<String> getDependencies() {
        return Arrays.asList("Towny");
    }

    @Override
    public void load(final Deadbolt plugin) {
        try {
            towny = ((Towny) Bukkit.getServer().getPluginManager().getPlugin("Towny")).getTownyUniverse();

            File configFile = new File(plugin.getDataFolder() + "/listeners/TownyListener.yml");
            checkFile(configFile);
            YamlConfiguration config = new YamlConfiguration();
            config.load(configFile);
            denyWilderness = config.getBoolean("deny_wilderness", denyWilderness);
            wildernessOverride = config.getBoolean("wilderness_override", wildernessOverride);
            mayorOverride = config.getBoolean("mayor_override", mayorOverride);
            assistantOverride = config.getBoolean("assistant_override", assistantOverride);
        } catch (FileNotFoundException ex) {
            Deadbolt.logger.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Deadbolt.logger.log(Level.SEVERE, null, ex);
        } catch (InvalidConfigurationException ex) {
            Deadbolt.logger.log(Level.SEVERE, null, ex);
        }
    }

    private String truncate(String text) {
        if (text.matches(patternBracketTooLong))
            return "[" + text.substring(1, 14) + "]";
        return text;
    }

    private void checkFile(File file) {
        try {
            if (!file.exists()) {
                file.createNewFile();
                FileWriter fw = new FileWriter(file);
                fw.write("deny_wilderness:      false  #Denies protecting of blocks in the wild\n");
                fw.write("wilderness_override:  false  #Allows ANYONE to break open locks in the wilderness\n");
                fw.write("mayor_override:       false  #Allows Mayors to break open locks in their town\n");
                fw.write("assistant_override:   false  #Allows Assistants to break open locks in their town\n");
                fw.close();
                Deadbolt.logger.log(Level.INFO, "[Deadbolt][" + this.getClass().getSimpleName() + "] Retrieved file " + file.getName());
            }
        } catch (IOException ex) {
            Deadbolt.logger.log(Level.SEVERE, "[Deadbolt][" + this.getClass().getSimpleName() + "] Error retrieving " + file.getName());
        }
    }

    @Override
    public boolean canPlayerInteract(Deadbolted db, PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        try {
            for (TownyWorld tw : towny.getWorlds()) {
                if (tw.getName().equalsIgnoreCase(player.getWorld().getName()) && tw.isUsingTowny()) {
                    Resident resident = towny.getResident(player.getName());

                    Town town = resident.getTown();
                    if (db.getUsers().contains(truncate("[" + town.getName().toLowerCase() + "]"))) { //town check
                        return true;
                    }
                    
                    if (db.getUsers().contains(truncate("+" + town.getName().toLowerCase() + "+"))
                            && (town.getMayor().equals(resident) || town.getAssistants().contains(resident)))//town assistant check
                        return true;

                    Nation nation = town.getNation();
                    if (db.getUsers().contains(truncate("[" + nation.getName() + "]").toLowerCase()))//nation check
                        return true;
                    if (db.getUsers().contains(truncate("+" + nation.getName().toLowerCase() + "+"))
                            && (nation.getCapital().getMayor().equals(resident) || nation.getAssistants().contains(resident)))//nation assistant check
                        return true;

                    if (wildernessOverride && towny.isWilderness(block))
                        return true;
                    if (mayorOverride)
                        return towny.getTownBlock(block.getLocation()).getTown().getMayor().equals(resident);
                    if (assistantOverride)
                        return towny.getTownBlock(block.getLocation()).getTown().getAssistants().contains(resident);
                    break;
                }
            }
        } catch (Exception ex) {
        }
        return super.canPlayerInteract(db, event);
    }

    @Override
    public boolean canSignChange(Deadbolted db, SignChangeEvent event) {
        //DEFAULT return true;
        return askTowny(event.getPlayer(), event.getBlock());
    }

    @Override
    public boolean canSignChangeQuick(Deadbolted db, PlayerInteractEvent event) {
        //DEFAULT return true;
        return askTowny(event.getPlayer(), event.getClickedBlock());
    }

    private boolean askTowny(Player player, Block block) {
        //OP check
        if (Deadbolt.instance.config.useOPlist && player.isOp())
            return true;

        //is this world using towny?
        for (TownyWorld townyWorld : towny.getWorlds())
            if (townyWorld.getName().equalsIgnoreCase(block.getWorld().getName()) && !townyWorld.isUsingTowny())
                return true;

        //wilderness check
        if (towny.isWilderness(block)) {
            if (denyWilderness) {
                Deadbolt.instance.config.sendMessage(player, ChatColor.RED, "You can only protect blocks inside of a town");
                return false;
            } else {
                return true;
            }
        }

        try {
            Resident resident = towny.getResident(player.getName());
            TownBlock townBlock = towny.getTownBlock(block.getLocation());

            //Owner
            if (resident.hasTownBlock(townBlock))
                return true;

            //Assistant
            if (townBlock.hasTown() && townBlock.getTown().getAssistants().contains(resident))
                return true;

            //Mayor
            if (townBlock.hasTown() && townBlock.getTown().getMayor().equals(resident))
                return true;

            //King
            if (townBlock.hasTown() && townBlock.getTown().hasNation() && townBlock.getTown().getNation().isKing(resident))
                return true;

            if (townBlock.hasResident()) {
                TownyPermission plotPerms = townBlock.getPermissions();

                //Owner friend
                if (plotPerms.residentBuild && isFriend(resident, townBlock))
                    return true;

                //Ally
                boolean isAlly = isAlly(resident, townBlock);
                if (plotPerms.allyBuild && isAlly)
                    return true;

                //Outsider
                if (plotPerms.outsiderBuild && !isAlly)
                    return true;

            } else {
                TownyPermission townPerms = townBlock.getTown().getPermissions();

                //Member
                boolean isResident = isResident(resident, townBlock);
                if (townPerms.residentBuild && isResident)
                    return true;

                //Ally
                boolean isAlly = isAlly(resident, townBlock);
                if (townPerms.allyBuild && isAlly)
                    return true;

                //Outsider
                if (townPerms.outsiderBuild && !isResident && !isAlly)
                    return true;
            }

        } catch (NotRegisteredException ex) {
        }
        return false;
    }

    private boolean isResident(Resident resident, TownBlock townBlock) {
        try {
            if (townBlock.getTown().hasResident(resident))
                return true;
        } catch (NotRegisteredException ex) {
        }
        return false;
    }

    private boolean isFriend(Resident resident, TownBlock townBlock) {
        try {
            if (townBlock.getResident().hasFriend(resident))
                return true;
        } catch (NotRegisteredException ex) {
        }

        return false;
    }

    private boolean isAlly(Resident resident, TownBlock townBlock) {
        try {
            //Ally by town resident
            if (townBlock.getTown().hasResident(resident))
                return true;
        } catch (NotRegisteredException ex) {
        }

        try {
            //Ally by direct Nation membership
            if (townBlock.getTown().getNation().hasTown(resident.getTown()))
                return true;
        } catch (NotRegisteredException ex) {
        }

        try {
            //Ally by Nation alliance
            if (townBlock.getTown().getNation().hasAlly(resident.getTown().getNation()))
                return true;
        } catch (NotRegisteredException ex) {
        }

        return false;
    }
}
