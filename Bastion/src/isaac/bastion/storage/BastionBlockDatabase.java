package isaac.bastion.storage;

import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import isaac.bastion.manager.ConfigManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;

public class BastionBlockDatabase implements BastionBlockStorage {
    private Database db;
    static public String bastionBlocksTable;

    private PreparedStatement getAllBastionsForWorld;
    private PreparedStatement addBastion;
    private PreparedStatement updateBastion;

    public BastionBlockDatabase(ConfigManager config, Logger logger) {
        db = new Database(config.getHost(), config.getPort(), config.getDatabase(), config.getUsername(),
                config.getPassword(), config.getPrefix(), logger);

        bastionBlocksTable = "bastion_blocks";
        db.connect();
        if (db.isConnected()) {
            initialize();
        }
    }

	// Check if database is connected and if not reconnect. BLOCKING
    private void reconnect() {
        if (!db.isConnected()) {
            db.connect();
            initialize();
        }
    }

    private void initialize(){
        String createTable="CREATE TABLE IF NOT EXISTS "+ bastionBlocksTable +" ("
                + "bastion_id int(10) unsigned NOT NULL AUTO_INCREMENT,"
                + "loc_x int(10), "
                + "loc_y int(10), "
                + "loc_z int(10), "
                + "loc_world varchar(40) NOT NULl, "
                + "placed bigint(20) Unsigned, "
                + "fraction float(20) Unsigned, "
                + "PRIMARY KEY (`bastion_id`)"
                + ");";
        db.execute(createTable);

        getAllBastionsForWorld = db.prepareStatement("SELECT * FROM "+ bastionBlocksTable +" WHERE loc_world=?;");
        addBastion = db.prepareStatement("INSERT INTO "+ BastionBlockDatabase.bastionBlocksTable +" (loc_x,loc_y,loc_z,loc_world,placed,fraction) VALUES(?,?,?,?,?,?);");
        updateBastion = db.prepareStatement("UPDATE "+ BastionBlockDatabase.bastionBlocksTable +" set placed=?,fraction=? where bastion_id=?;");
    }

    public Enumeration<BastionBlock> getAllBastions(World world) {
        return new BastionBlockEnumerator(world);
    }


    // saves a new bastion into the database note will create double entries if bastion already exists blocking
    public int save(Location location, long placed, double balance) {
        reconnect();
        try {
            addBastion.setInt   (1, location.getBlockX());
            addBastion.setInt   (2, location.getBlockY());
            addBastion.setInt   (3, location.getBlockZ());
            addBastion.setString(4, location.getWorld().getName());
            addBastion.setLong(5, placed);
            addBastion.setDouble(6, balance);
            addBastion.execute();
            return db.getInteger("SELECT LAST_INSERT_ID();");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    };

    // updates placed and balance in db blocking
    public void update(Location location, long placed, double balance, int id){
        reconnect();
        try {
            updateBastion.setLong(1, placed);
            updateBastion.setDouble(2, balance);
            updateBastion.setInt(3, id);
            updateBastion.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

	// blocking
    public void delete(int id){
        reconnect();
        db.execute("DELETE FROM " + BastionBlockDatabase.bastionBlocksTable + " WHERE bastion_id=" + id + ";");
    }

    class BastionBlockEnumerator implements Enumeration<BastionBlock> {
        World world;
        ResultSet result;
        BastionBlock next;
        public BastionBlockEnumerator(World world){
            this.world = world;

            if(!db.isConnected()){
                next = null;
                return;
            }
            try{
                getAllBastionsForWorld.setString(1, world.getName());
                result = getAllBastionsForWorld.executeQuery();
                next = nextBastionBlock();
            } catch(SQLException e){
                next = null;
                result = null;
            }
        }
        @Override
        public boolean hasMoreElements() {
            return next != null;
        }

        @Override
        public BastionBlock nextElement() {
            BastionBlock result = next;
            next = nextBastionBlock();
            return result;
        }
        public BastionBlock nextBastionBlock() {
            int x,y,z,id;
            long placed;
            float balance;
            try {
                if (result == null || !result.next()) {
                    result = null;
                    return null;
                }
                x = result.getInt("loc_x");
                y = result.getInt("loc_y");
                z = result.getInt("loc_z");

                id = result.getInt("bastion_id");
                placed = result.getLong("placed");
                balance = result.getFloat("fraction");

            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
            Location loc = new Location(world, x, y, z);
            return new BastionBlock(loc, placed, balance, id);
        }
    }
}
