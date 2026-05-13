package pk.ajneb97.tasks;

import pk.ajneb97.PlayerKits2;
import pk.ajneb97.utils.FoliaScheduler;

public class PlayerDataSaveTask {

	private PlayerKits2 plugin;
	private FoliaScheduler.Task task;
	private volatile boolean end;
	public PlayerDataSaveTask(PlayerKits2 plugin) {
		this.plugin = plugin;
		this.end = false;
	}

	public void end() {
		end = true;
		if(task != null) {
			task.cancel();
			task = null;
		}
	}

	public void start(int seconds) {
		long ticks = seconds * 20L;
		task = FoliaScheduler.runAsyncTimer(plugin, () -> {
			if(end) {
				return;
			}
			execute();
		}, 1L, ticks);
	}

	public void execute() {
		plugin.getConfigsManager().getPlayersConfigManager().saveConfigs();
	}
}
