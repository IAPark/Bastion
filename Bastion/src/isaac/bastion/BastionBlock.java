package isaac.bastion;


import isaac.bastion.storage.BastionBlockSet;
import isaac.bastion.storage.BastionBlockDatabase;
import isaac.bastion.storage.BastionBlockStorage;
import isaac.bastion.storage.Database;
import isaac.bastion.util.QTBox;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;

import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.reinforcement.PlayerReinforcement;
import vg.civcraft.mc.citadel.reinforcement.Reinforcement;


public class BastionBlock implements QTBox, Comparable<BastionBlock>
{	
	public static int MIN_BREAK_TIME; //Minimum time between erosions that count
	private static int EROSION_TIME; //time between auto erosion. If 0 never called.
	private static int SCALING_TIME; //time between creation and max strength/maturity

	private static int RADIUS_SQUARED; //radius blocked squared
	private static int RADIUS; //radius blocked

	private static double BLOCK_TO_PEARL_SCALE; //factor between reinforcement removed by placing blocks and from blocking pearls
	public static boolean ONLY_BLOCK_PEARLS_ON_MATURE; //only block pearls after maturity has been reached

	private static boolean first=true;




	private Location location; 
	private double balance=0; //the amount remaining still to be eroded after the whole part has been removed
	private int strength; //current durability
	private long placed; //time when the bastion block was created

	private volatile boolean inDB = false;
	private volatile int id = -1;

	private int taskId; // the id of the task associated with erosion
	private static Random random; //used only to offset the erosion tasks
	public static BastionBlockSet set; 

	//constructor for new blocks. Reinforcement must be passed because it does not exist at the time of the reinforcement event.
	public BastionBlock(Location location, PlayerReinforcement reinforcement){
		this.location = location;
		placed=System.currentTimeMillis();
		id = set.size();

		strength=reinforcement.getDurability();
		setup();

	}
	//constructor for blocks loaded from database
	public BastionBlock(Location location,long placed,float balance,int ID){
		this.id = ID;
		this.location = location;

		this.placed = placed;
		this.balance = balance;

		inDB = true;

		PlayerReinforcement reinforcement = getReinforcement();
		if(reinforcement != null){
			strength = reinforcement.getDurability();
			setup();
		} else{
			strength=0;
			close();
		}

	}

	//called by both constructor to do the things they share
	private void setup(){
		if(first){
			firstime_setup();
		}

		if(EROSION_TIME!=0){
			taskId=registerTask();
		}

	}
	//called if this is the first Bastion block created to set up the static variables
	private void firstime_setup(){

		if(Bastion.getConfigManager().getBastionBlockMaxBreaks()!=0){ //we really should never have 0
			//convert getBastionBlockMaxBreaks() from breaks per second to
			//invulnerability time in milliseconds 
			MIN_BREAK_TIME=(1000*60)/Bastion.getConfigManager().getBastionBlockMaxBreaks();
		} else{
			MIN_BREAK_TIME=0; //if we do default to no invulnerability time
		}

		SCALING_TIME=Bastion.getConfigManager().getBastionBlockScaleTime();

		if(Bastion.getConfigManager().getBastionBlockErosion()!=0){ //0 disables constant erosion
			//Convert getBastionBlockErosion() from erosion per day to time between erosions
			EROSION_TIME=(1000*60*60*24*20)/Bastion.getConfigManager().getBastionBlockErosion(); 
		} else{
			EROSION_TIME=0;
		}

		BLOCK_TO_PEARL_SCALE=Bastion.getConfigManager().getEnderPearlErosionScale();
		ONLY_BLOCK_PEARLS_ON_MATURE=Bastion.getConfigManager().getEnderPearlRequireMaturity();

		RADIUS=Bastion.getConfigManager().getBastionBlockEffectRadius();
		RADIUS_SQUARED=RADIUS*RADIUS;

		random=new Random();
		first=false;

	}
	//called to register the erosion task
	private int registerTask(){
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		return scheduler.scheduleSyncRepeatingTask(Bastion.getPlugin(),
				new BukkitRunnable(){
			public void run(){
				erode(1);
			}
		},
		random.nextInt(EROSION_TIME), EROSION_TIME);
	}

	//saves a new bastion into the database note will create double entries if bastion already exists
	public void save(final BastionBlockStorage storage){
		if(!inDB){
			inDB = true;
			final double balance = this.balance; // balance can change so we'll store the current version and save that
			new BukkitRunnable() {
				@Override
				public void run() {
					id = storage.save(location, placed, balance);
					if (id < 0) {
						inDB = false;
					}
				}
			}.runTaskAsynchronously(Bastion.getPlugin());
		} else {
			Bastion.getPlugin().getLogger().warning("tried to save BastionBlock that was in DB\n " + toString());
			
		}
	}
	
	//updates placed and balance in db
	public void update(final BastionBlockStorage storage){
		if (inDB){
			final double balance = this.balance; // balance can change so we'll store the current version and save that

			// we need to handle database access on another thread
			new BukkitRunnable() {
				@Override
				public void run() {
					storage.update(location, placed, balance, id);
				}
			}.runTaskAsynchronously(Bastion.getPlugin());
		} else {
			Bastion.getPlugin().getLogger().warning("tried to update BastionBlock that was not in DB \n " + toString());
			save(storage);
		}
	}

	public void delete(final BastionBlockStorage storage){
		if(!inDB) {
			Bastion.getPlugin().getLogger().warning("tried to delete Bastion that wasn't saved \n " + toString());
			return;
		}

		inDB = false;
		new BukkitRunnable() {
			@Override
			public void run() {
				inDB = !storage.delete(id);
			}
		};
	}


	/**
	 *  Gets the percentage of the reinforcement that should erode from the block
	 * @return The percentage that should erode
	 */
	public double erosionFromBlock(){
		double scaleStart=Bastion.getConfigManager().getBastionBlockScaleFacStart();
		double scaleEnd=Bastion.getConfigManager().getBastionBlockScaleFacEnd();
		
		// This needs to be a long or it will roll over after 21 days!
		long time = System.currentTimeMillis() - placed;

		if(SCALING_TIME == 0){
			return scaleStart;
		} else if(time < SCALING_TIME){
			return (((scaleEnd - scaleStart) / (float)SCALING_TIME) * time + scaleStart);
		} else{
			return scaleEnd;
		}
	}

	//currently very simple but gives easy options to change
	public double erosionFromPearl(){
		return erosionFromBlock() * BLOCK_TO_PEARL_SCALE;
	}

	public boolean inField(Location loc){
		if (((loc.getBlockX() - location.getX()) * (float)(loc.getBlockX() - location.getX()) +
				(loc.getBlockZ() - location.getZ()) * (float)(loc.getBlockZ() - location.getZ()) >= RADIUS_SQUARED)
				|| (loc.getBlockY() <= location.getY())) {
			return false;
		}
		return true;
	}
	//checks if a player would be allowed to remove the Bastion block

	public boolean canRemove(Player player){

		PlayerReinforcement reinforcement = getReinforcement();

		if(reinforcement!=null){
			return reinforcement.isBypassable(player); //should return true if founder or moderator, but I feel this is more consistant
		}
		return true;
	}

	//checks if a player would be allowed to place
	public boolean canPlace(Player player){

		PlayerReinforcement reinforcement = getReinforcement();

		if (reinforcement == null) return true;
		if (player == null) return false;

		if (reinforcement.isAccessible(player)) return true;

		return false;
	}

	public boolean oneCanPlace(List<UUID> players){
		PlayerReinforcement reinforcement = getReinforcement();
		//the object will have been closed if null but we still don't want things to crash
		if (reinforcement == null)
			return true; 

		for (UUID player: players){
			if (player != null)
				if (reinforcement.isAccessible(player))
					return true;
		}

		return false;
	}


	//returns if the Bastion's strength is at zero and it should be removed
	public boolean shouldCull(){
		if(strength-balance > 0){
			return false;
		} else{
			return true;
		}
	}

	//removes a set amount of durability from the reinforcement
	public void erode(double amount){
		double toBeRemoved=balance+amount;

		int wholeToRemove=(int) toBeRemoved;
		double fractionToRemove=(double) toBeRemoved-wholeToRemove;

		Reinforcement reinforcement = getReinforcement();

		if (reinforcement != null) {
			strength=reinforcement.getDurability();
		} else return;

		strength-=wholeToRemove;
		balance=fractionToRemove;

		reinforcement.setDurability(strength);

		set.updated(this);

		if(shouldCull())
			destroy();
	}



	public void mature(){
		placed -= SCALING_TIME;
	}
	
	public boolean isMature(){
		return System.currentTimeMillis() - placed >= SCALING_TIME;
	}

	private PlayerReinforcement getReinforcement(){
		PlayerReinforcement reinforcement = (PlayerReinforcement) Citadel.getReinforcementManager().
				getReinforcement(location.getBlock());
		if(reinforcement instanceof PlayerReinforcement){
			return reinforcement;
		} else {
			close();
			Bastion.getPlugin().getLogger().log(Level.SEVERE, "Reinforcement removed without removing Bastion. Fixed");
		}
		return null;
	}

	public UUID getOwner(){
		return getReinforcement().getGroup().getOwner();
	}

	public Location getLocation(){
		return location;
	}

	static public int getRadiusSquared(){
		return RADIUS_SQUARED;
	}
	public long getId(){
		return id;
	}

	//need to use SparseQuadTree
	@Override
	public int qtXMin() {
		return location.getBlockX()-RADIUS;
	}

	@Override
	public int qtXMid() {
		return location.getBlockX();
	}

	@Override
	public int qtXMax() {
		return location.getBlockX()+RADIUS;
	}

	@Override
	public int qtZMin() {
		return location.getBlockZ()-RADIUS;
	}

	@Override
	public int qtZMid() {
		return location.getBlockZ();
	}

	@Override
	public int qtZMax() {
		return location.getBlockZ()+RADIUS;
	}


	public String toString(){
		SimpleDateFormat dateFormator = new SimpleDateFormat("M/d/yy H:m:s");
		String result="Dev text: ";

		PlayerReinforcement reinforcement = getReinforcement();

		double scaleTime_as_hours=0;
		if(SCALING_TIME==0){
			result+="Maturity timers are disabled \n";
		} else{
			scaleTime_as_hours = ((double) SCALING_TIME)/(1000*60*60);
		}
		if (reinforcement instanceof PlayerReinforcement) {
			strength=reinforcement.getDurability();

			result+="Current Bastion reinforcement: "+String.valueOf((double) strength-balance)+'\n';

			result+="Maturity time is ";
			result+=String.valueOf(scaleTime_as_hours)+'\n';

			result+="Which means  " + String.valueOf(erosionFromBlock()) + " will removed after every blocked placeemnt"+'\n';

			result+="Placed on "+dateFormator.format(new Date(placed))+'\n';
			result+="by group "+reinforcement.getGroup().getName() + '\n';
			result+="At: "+location.toString();
		}



		return result;
	}
	public String infoMessage(boolean dev,Player asking){
		if(dev){
			return ChatColor.GREEN+this.toString();
		}

		String result="";

		//PlayerReinforcement reinforcement = (PlayerReinforcement) Citadel.getReinforcementManager().getReinforcement(location.getBlock());
		//Faction fac;
		double fractionOfMaturityTime=0;
		if(SCALING_TIME==0){
			fractionOfMaturityTime=1;
		} else{
			fractionOfMaturityTime=((double) (System.currentTimeMillis()-placed))/SCALING_TIME;
		}
		if(fractionOfMaturityTime==0){
			result = ChatColor.GREEN+"No strength";
		} else if(fractionOfMaturityTime < 0.25){
			result = ChatColor.GREEN+"Some strength";
		} else if(fractionOfMaturityTime < 0.5){
			result = ChatColor.GREEN+"Low strength";
		} else if(fractionOfMaturityTime < 0.75){
			result = ChatColor.GREEN+"Moderate strength";
		} else if(fractionOfMaturityTime < 1){
			result = ChatColor.GREEN+"High strength";
		} else if(fractionOfMaturityTime>=1){
			result = ChatColor.GREEN+"Full strength";
		}


		return result;
	}
	@Override
	public int compareTo(BastionBlock other) {
		int thisX=location.getBlockX();
		int thisY=location.getBlockY();
		int thisZ=location.getBlockZ();

		int otherX=other.location.getBlockX();
		int otherY=other.location.getBlockY();
		int otherZ=other.location.getBlockZ();

		if(thisX<otherX)
			return -1;
		if(thisX>otherX)
			return 1;

		if(thisY<otherY)
			return -1;
		if(thisY>otherY)
			return 1;

		if(thisZ<otherZ)
			return -1;
		if(thisZ>otherZ)
			return 1;

		return 0;
	}

	
	//removes Bastion from database and destroys the block at the location
	public void destroy(){
		if (Bastion.getConfigManager().getDestroy())
			location.getBlock().setType(Material.AIR);
		close();
	}

	//removes Bastion from 
	public void close(){
		if(!set.contains(this)){
			Bastion.getPlugin().getLogger().warning("tried to close already closed Bastion");
			//return; //already not in don't need to remove
		}

		if(EROSION_TIME!=0){
			BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
			scheduler.cancelTask(taskId);
		}
		set.remove(this);

		Bastion.getPlugin().getLogger().info("Removed bastion "+id);
		Bastion.getPlugin().getLogger().info("Had been placed on "+placed);
		Bastion.getPlugin().getLogger().info("At "+location);
	}
}
