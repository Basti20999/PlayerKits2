package pk.ajneb97.tasks;

import pk.ajneb97.PlayerKits2;
import pk.ajneb97.utils.FoliaScheduler;

public class PlayerDataSaveTask {

	private final PlayerKits2 plugin;
	private FoliaScheduler.Task task;

	public PlayerDataSaveTask(PlayerKits2 plugin) {
		this.plugin = plugin;
	}

	public void end() {
		if(task != null) {
			task.cancel();
			task = null;
		}
	}

	public void start(int seconds) {
		long ticks = seconds * 20L;
		task = FoliaScheduler.runAsyncTimer(plugin, this::execute, ticks, ticks);
	}

	public void execute() {
		plugin.getConfigsManager().getPlayersConfigManager().saveConfigs();
	}
}
