package isaac.bastion.storage;

import isaac.bastion.BastionBlock;
import isaac.bastion.manager.ConfigManager;
import isaac.bastion.manager.EnderPearlManager;
import isaac.bastion.util.QTBox;
import isaac.bastion.util.SparseQuadTree;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

public class BastionBlockSet implements Set<BastionBlock>,
Iterable<BastionBlock> {
    private Map<World,SparseQuadTree> blocks;
    private Set<BastionBlock> changed;
    public Set<BastionBlock> bastionBlocks;
    private BastionBlockStorage storage;
    private int task;
    private ConfigManager config;
    private Logger logger;


    public BastionBlockSet(BastionBlockStorage storage, ConfigManager config, Logger logger) {
        this.storage = storage;
        changed = new HashSet<>();
        this.config = config;
        this.logger = logger;

        blocks = new HashMap<World, SparseQuadTree>();
        bastionBlocks = new TreeSet<BastionBlock>();

        BastionBlock.set = this;
    }

    // note only for BastionBlocks already in db
    public void updated(BastionBlock updated){
        if(!changed.contains(updated)) {
            changed.add(updated);
        }
    }


    public void close(){
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.cancelTask(task);
        update();
    }

    public void load(){
        int enderSeachRadius=EnderPearlManager.MAX_TELEPORT+100;
        for(World world : Bukkit.getWorlds()){
            Enumeration<BastionBlock> forWorld=storage.getAllBastions(world);
            SparseQuadTree bastionsForWorld=new SparseQuadTree(enderSeachRadius);
            while(forWorld.hasMoreElements()){
                BastionBlock toAdd=forWorld.nextElement();
                bastionBlocks.add(toAdd);
                bastionsForWorld.add(toAdd);
            }
            blocks.put(world, bastionsForWorld);
        }
    }

    public void removeGhostBlocks(){
        Bukkit.getLogger().log(Level.INFO, "Bastion is beginning ghost block check.");
        for (BastionBlock block: this){
            if (block.getLocation().getBlock().getType() != config.getBastionBlockMaterial()){
                Bukkit.getLogger().log(Level.INFO, "Bastion removed a block at: " + block.getLocation() + ". If it is still"
                        + " there, there is a problem...");
                block.delete(storage);
            }
        }
        Bukkit.getLogger().log(Level.INFO, "Bastion has ended ghost block check.");
    }

    public Set<QTBox> forLocation(Location loc){
        return blocks.get(loc.getWorld()).find(loc.getBlockX(), loc.getBlockZ());
    }


    public Set<BastionBlock> getPossibleTeleportBlocking(Location loc, double maxDistance){
        Set<QTBox> boxes = blocks.get(loc.getWorld()).find(loc.getBlockX(), loc.getBlockZ(),true);

        double maxDistanceSquared = maxDistance * maxDistance;

        Set<BastionBlock> result = new TreeSet<BastionBlock>();

        for(QTBox box : boxes){
            if(box instanceof BastionBlock){
                BastionBlock bastion = (BastionBlock)box;
                if(bastion.getLocation().distanceSquared(loc) < maxDistanceSquared && (!config.getEnderPearlRequireMaturity() || bastion.isMature()))
                    result.add(bastion);
            }
        }
        return result;
    }

    // Get all Bastions that cover a location
    public Set<BastionBlock> getFields(Location loc){
        Set<? extends QTBox> boxes = blocks.get(loc.getWorld()).find(loc.getBlockX(), loc.getBlockZ());
        Set<BastionBlock> bastions = null;

        if(boxes.size() > 0 && boxes.iterator().next() instanceof BastionBlock) {
            bastions = (Set<BastionBlock>) boxes;
        }

        if(bastions == null)
            return new CopyOnWriteArraySet<BastionBlock>();

        Iterator<BastionBlock> i = bastions.iterator();

        while (i.hasNext()){
            BastionBlock bastion = i.next();
            if (!bastion.inField(loc)){
                i.remove();
            }
        };
        return bastions;
    }

    // Get Bastions that protect the location from all the players
    public Set<BastionBlock> getBlockingBastions(Location loc, List<UUID> players){
        Set<BastionBlock> bastions = getFields(loc);

        for (Iterator<BastionBlock> i = bastions.iterator(); i.hasNext();) {
            if (!i.next().oneCanPlace(players)) {
                i.remove();
            }
        }

        return bastions;
    }


    public BastionBlock getBastionBlock(Location loc) {
        Set<? extends QTBox> possible=forLocation(loc);
        for(QTBox box: possible){
            BastionBlock bastion=(BastionBlock) box;
            if(bastion.getLocation().equals(loc))
                return bastion;
        }
        return null;
    }

    public int update(){
        int count = changed.size();
        for(BastionBlock toUpdate: changed) toUpdate.update(storage);
        changed.clear();
        logger.info("updated " + count + " blocks");
        return count;
    }

    @Override
    public boolean add(BastionBlock toAdd) {
        toAdd.save(storage);

        bastionBlocks.add(toAdd);
        blocks.get(toAdd.getLocation().getWorld()).add(toAdd);
        return false;
    }

    public boolean remove(BastionBlock toRemove){
        boolean in_set;

        if(toRemove == null)
            return false;
        toRemove.delete(storage); //maybe should cache and run in different thread

        in_set = bastionBlocks.remove(toRemove);
        changed.remove(toRemove);

        SparseQuadTree forWorld=blocks.get(toRemove.getLocation().getWorld());
        if(forWorld!=null){
            forWorld.remove(toRemove);
        }
        return in_set;
    }


    @Override
    public Iterator<BastionBlock> iterator() {
        return bastionBlocks.iterator();
    }
    @Override
    public boolean addAll(Collection<? extends BastionBlock> toAdd) {
        for(BastionBlock out: toAdd)
            if(!add(out))
                return false;
        return true;
    }
    @Override
    public void clear() {
        blocks.clear();
        bastionBlocks.clear();
        changed.clear();
    }
    @Override
    public boolean contains(Object in) { //compares by id not by pointer
        if(!(in instanceof BastionBlock))
            throw new IllegalArgumentException("contains only excepts a BastionBlock");
        BastionBlock toTest=(BastionBlock) in;
        return bastionBlocks.contains(toTest);
    }
    @Override
    public boolean containsAll(Collection<?> arg0) {
        for(Object in: bastionBlocks)
            if(!contains(in))
                return false;
        return true;
    }
    @Override
    public boolean isEmpty() {
        return bastionBlocks.isEmpty();
    }
    @Override
    public boolean remove(Object in) {
        if(in == null){
            return false;
        } else if (in instanceof BastionBlock){
            return remove((BastionBlock) in);
        } else if (in instanceof Location){
            return remove(getBastionBlock((Location) in));
        } else{
            throw new IllegalArgumentException("you didn't provide a BastionBlock or Location");
        }
    }

    @Override
    public boolean removeAll(Collection<?> toRemove) {
        if(toRemove.size()==0)
            return true;
        for(Object block : toRemove){
            remove(block);
        }
        return false;
    }
    @Override
    public boolean retainAll(Collection<?> arg0) {
        return false;
    }
    @Override
    public int size() {
        return bastionBlocks.size();
    }
    @Override
    public Object[] toArray() {
        return bastionBlocks.toArray();
    }
    @Override
    public <T> T[] toArray(T[] arg0) {
        return bastionBlocks.toArray(arg0);
    }



}
