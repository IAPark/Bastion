package isaac.bastion.listeners;

import isaac.bastion.commands.PlayersStates;
import isaac.bastion.manager.BastionBlockManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import vg.civcraft.mc.citadel.events.ReinforcementCreationEvent;
import vg.civcraft.mc.citadel.reinforcement.PlayerReinforcement;
import vg.civcraft.mc.namelayer.group.groups.PublicGroup;


public class BastionCreationListener implements Listener {

    // the material that when reinforced should be bastion blocks
    Material bastionMaterial;
    BastionBlockManager bastionBlockManager;

    public BastionCreationListener(Material bastionMaterial, BastionBlockManager bastionBlockManager) {
        this.bastionMaterial = bastionMaterial;
        this.bastionBlockManager = bastionBlockManager;
    }

    @EventHandler
    public void onReinforcement(ReinforcementCreationEvent event) {

        if (event.getBlock().getType() == bastionMaterial &&
                !PlayersStates.playerInMode(event.getPlayer(), PlayersStates.Mode.OFF) && event.getReinforcement() instanceof PlayerReinforcement) {

            PlayersStates.touchPlayer(event.getPlayer());

            PlayerReinforcement rein = (PlayerReinforcement) event.getReinforcement();
            if (rein.getGroup() instanceof PublicGroup){
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Bastion's cannot be reinforced under a PublicGroup.");
            }
            else{
                bastionBlockManager.addBastion(event.getBlock().getLocation(), rein);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Bastion block created");
            }
        }
    }
}
