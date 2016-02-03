package isaac.bastion.storage;


import isaac.bastion.BastionBlock;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Enumeration;

public interface BastionBlockStorage {
    void save(Location location, long placed, double balance, SaveListener saveListener);
    void update(Location location, long placed, double balance, int id, UpdateListener updateListener);
    void delete(int id, DeleteListener deleteListener);

    Enumeration<BastionBlock> getAllBastions(World world);

    interface SaveListener {
        void saved(int id);
    }

    interface UpdateListener {
        void updated(boolean worked);
    }

    interface DeleteListener {
        void deleted(boolean worked);
    }
}
