package isaac.bastion.listeners;



import isaac.bastion.manager.BastionBlockManager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.StructureGrowEvent;

public final class BlockListener implements Listener
{
	private BastionBlockManager bastionManager;

	public BlockListener(BastionBlockManager bastionManager)
	{
		this.bastionManager = bastionManager;
	}
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onBlockPlace(BlockPlaceEvent event) {
		bastionManager.handleBlockPlace(event);
	}

	@EventHandler (ignoreCancelled = true)
	public void waterflowed(BlockFromToEvent  event){
		bastionManager.handleFlowingWater(event);
	}

	@EventHandler (ignoreCancelled = true)
	public void treeGrew(StructureGrowEvent event){
		bastionManager.handleTreeGrowth(event);
	}

	@EventHandler (ignoreCancelled = true)
	public void pistionPushed(BlockPistonExtendEvent  event){
		bastionManager.handlePistonPush(event);
	}
	@EventHandler (ignoreCancelled = true)
	public void bucketPlaced(PlayerBucketEmptyEvent  event){
		bastionManager.handleBucketPlace(event);

	}
	@EventHandler (ignoreCancelled = true)
	public void dispensed(BlockDispenseEvent  event){
		bastionManager.handleDispensed(event);
	}
	@EventHandler (ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		bastionManager.handleBlockBreakEvent(event);
	}
}
