package isaac.bastion;




import isaac.bastion.commands.BastionCommandManager;
import isaac.bastion.commands.ModeChangeCommand;
import isaac.bastion.commands.PlayersStates.Mode;
import isaac.bastion.listeners.BastionListener;
import isaac.bastion.listeners.CommandListener;
import isaac.bastion.listeners.EnderPearlListener;
import isaac.bastion.manager.BastionBlockManager;
import isaac.bastion.manager.ConfigManager;
import isaac.bastion.storage.BastionBlockDatabase;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


public final class Bastion extends JavaPlugin
{
	private static BastionListener listener; ///Main listener
	private static Bastion plugin; ///Holds the plugin
	private static BastionBlockManager bastionManager; ///Most of the direct interaction with Bastions
	private static ConfigManager config; ///Holds the configuration

	private BukkitRunnable saveTask;
	
	public void onEnable()
	{
		//set the static variables
		plugin = this;
		config = new ConfigManager();
		bastionManager = new BastionBlockManager(new BastionBlockDatabase(config, getLogger(), plugin), config, getLogger());

		if(Bastion.getPlugin().isEnabled()){
			saveTask = new BukkitRunnable(){
				public void run(){
					bastionManager.set.update();
				}
			};
			saveTask.runTaskTimer(plugin, config.getTimeBetweenSaves(), config.getTimeBetweenSaves());
		}

		listener = new BastionListener();
		
		bastionManager.set.removeGhostBlocks();
		
		if(!this.isEnabled()) //check that the plugin was not disabled in setting up any of the static variables
			return;
		
		registerListeners();
		registerCommands();
	}
	
	//What the name says
	private void registerListeners(){
		getServer().getPluginManager().registerEvents(listener, this);
		getServer().getPluginManager().registerEvents(new CommandListener(), this);
		if(config.getEnderPearlsBlocked()) //currently everything to do with blocking pearls is part of EnderPearlListener. Needs changed
			getServer().getPluginManager().registerEvents(new EnderPearlListener(), this);
	}
	
	

	//Sets up the command managers
	private void registerCommands(){
		getCommand("Bastion").setExecutor(new BastionCommandManager());
		getCommand("bsi").setExecutor(new ModeChangeCommand(Mode.INFO));
		getCommand("bsd").setExecutor(new ModeChangeCommand(Mode.DELETE));
		getCommand("bso").setExecutor(new ModeChangeCommand(Mode.NORMAL));
		getCommand("bsb").setExecutor(new ModeChangeCommand(Mode.BASTION));
		getCommand("bsf").setExecutor(new ModeChangeCommand(Mode.OFF));
		getCommand("bsm").setExecutor(new ModeChangeCommand(Mode.MATURE));
	}

	public void onDisable()
	{
		if(bastionManager==null)
			return;
		bastionManager.close();//saves all Bastion Blocks
		saveTask.cancel();
	}

	public static BastionBlockManager getBastionManager()
	{
		return bastionManager;
	}
	public static Bastion getPlugin()
	{
		return plugin;
	}
	public static BastionListener getBastionBlockListener()
	{
		return listener;
	}
	public static ConfigManager getConfigManager(){
		return config;
	}

}