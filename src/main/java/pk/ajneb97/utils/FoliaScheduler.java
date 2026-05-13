package pk.ajneb97.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight scheduler abstraction that works transparently on both
 * Paper/Spigot and Folia. On Folia, the global region scheduler, region
 * scheduler, entity scheduler and async scheduler are dispatched via reflection,
 * which keeps this plugin compiling against the regular Paper API without
 * a hard dependency on the Folia-specific classes.
 */
public final class FoliaScheduler {

    private static final boolean FOLIA;
    private static Method getGlobalRegionScheduler;
    private static Method getAsyncScheduler;
    private static Method getRegionScheduler;
    private static Method entityGetScheduler;

    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;

    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method asyncRunAtFixedRate;

    private static Method regionExecute;

    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method entityRunAtFixedRate;

    private static Method taskCancel;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            folia = false;
        }
        FOLIA = folia;

        if (FOLIA) {
            try {
                Class<?> serverClass = Bukkit.getServer().getClass();
                getGlobalRegionScheduler = serverClass.getMethod("getGlobalRegionScheduler");
                getAsyncScheduler = serverClass.getMethod("getAsyncScheduler");
                getRegionScheduler = serverClass.getMethod("getRegionScheduler");
                entityGetScheduler = Class.forName("org.bukkit.entity.Entity").getMethod("getScheduler");

                Class<?> globalRegionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                Class<?> regionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
                Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Class<?> scheduledTask = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

                globalRun = globalRegionScheduler.getMethod("run", Plugin.class, java.util.function.Consumer.class);
                globalRunDelayed = globalRegionScheduler.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
                globalRunAtFixedRate = globalRegionScheduler.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);

                asyncRunNow = asyncScheduler.getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
                asyncRunDelayed = asyncScheduler.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class, TimeUnit.class);
                asyncRunAtFixedRate = asyncScheduler.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class, TimeUnit.class);

                regionExecute = regionScheduler.getMethod("execute", Plugin.class, org.bukkit.World.class, int.class, int.class, Runnable.class);

                entityRun = entityScheduler.getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class);
                entityRunDelayed = entityScheduler.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class);
                entityRunAtFixedRate = entityScheduler.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class, long.class);

                taskCancel = scheduledTask.getMethod("cancel");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to initialize Folia scheduler bridge", e);
            }
        }
    }

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /** Runs the task on the next global region tick (or main thread on Bukkit). */
    public static Task run(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Object scheduler = invoke(getGlobalRegionScheduler, Bukkit.getServer());
            Object scheduled = invoke(globalRun, scheduler, plugin, toConsumer(task));
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
        return new Task(bukkitTask);
    }

    public static Task runLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (FOLIA) {
            Object scheduler = invoke(getGlobalRegionScheduler, Bukkit.getServer());
            Object scheduled = invoke(globalRunDelayed, scheduler, plugin, toConsumer(task), delay);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        return new Task(bukkitTask);
    }

    public static Task runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            Object scheduler = invoke(getGlobalRegionScheduler, Bukkit.getServer());
            Object scheduled = invoke(globalRunAtFixedRate, scheduler, plugin, toConsumer(task), delay, period);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new Task(bukkitTask);
    }

    public static Task runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Object scheduler = invoke(getAsyncScheduler, Bukkit.getServer());
            Object scheduled = invoke(asyncRunNow, scheduler, plugin, toConsumer(task));
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        return new Task(bukkitTask);
    }

    public static Task runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (FOLIA) {
            Object scheduler = invoke(getAsyncScheduler, Bukkit.getServer());
            Object scheduled = invoke(asyncRunDelayed, scheduler, plugin, toConsumer(task), delay * 50L, TimeUnit.MILLISECONDS);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        return new Task(bukkitTask);
    }

    public static Task runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            Object scheduler = invoke(getAsyncScheduler, Bukkit.getServer());
            Object scheduled = invoke(asyncRunAtFixedRate, scheduler, plugin, toConsumer(task),
                    delay * 50L, period * 50L, TimeUnit.MILLISECONDS);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        return new Task(bukkitTask);
    }

    /** Runs the task on the entity's tick region. The retired callback is fired if the entity is removed. */
    public static Task runForEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (FOLIA) {
            Object scheduler = invoke(entityGetScheduler, entity);
            Object scheduled = invoke(entityRun, scheduler, plugin, toConsumer(task), retired);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
        return new Task(bukkitTask);
    }

    public static Task runForEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retired, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (FOLIA) {
            Object scheduler = invoke(entityGetScheduler, entity);
            Object scheduled = invoke(entityRunDelayed, scheduler, plugin, toConsumer(task), retired, delay);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        return new Task(bukkitTask);
    }

    public static Task runForEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable retired,
                                         long delayTicks, long periodTicks) {
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            Object scheduler = invoke(entityGetScheduler, entity);
            Object scheduled = invoke(entityRunAtFixedRate, scheduler, plugin, toConsumer(task), retired, delay, period);
            return new Task(scheduled);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new Task(bukkitTask);
    }

    /** Runs the task on the region that owns the given location. */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            Object scheduler = invoke(getRegionScheduler, Bukkit.getServer());
            invoke(regionExecute, scheduler, plugin, location.getWorld(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static java.util.function.Consumer<Object> toConsumer(Runnable runnable) {
        return ignored -> runnable.run();
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke scheduler method " + method.getName(), e);
        }
    }

    /** Opaque task handle that can be cancelled regardless of platform. */
    public static final class Task {

        private final Object handle;

        private Task(Object handle) {
            this.handle = handle;
        }

        public void cancel() {
            if (handle == null) {
                return;
            }
            if (handle instanceof BukkitTask) {
                ((BukkitTask) handle).cancel();
                return;
            }
            try {
                taskCancel.invoke(handle);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to cancel Folia task", e);
            }
        }
    }
}
