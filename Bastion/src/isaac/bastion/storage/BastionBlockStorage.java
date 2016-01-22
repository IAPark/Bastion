package isaac.bastion.storage;


import isaac.bastion.BastionBlock;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Enumeration;

public interface BastionBlockStorage {
    int save(Location location, long placed, double balance);
    boolean update(Location location, long placed, double balance, int id);
    boolean delete(int id);

    Enumeration<BastionBlock> getAllBastions(World world);
}
