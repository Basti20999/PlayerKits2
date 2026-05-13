package pk.ajneb97.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Lightweight scheduler bridge that works transparently on both
 * Paper/Spigot and Folia. On Folia the global region, region, entity and async
 * schedulers are dispatched via reflection, which keeps this plugin compiling
 * against the regular Paper API without a hard dependency on the Folia classes.
 */
public final class FoliaScheduler {

    private static final boolean FOLIA;

    // Folia scheduler accessors (null on non-Folia).
    private static final Method GET_GLOBAL_SCHEDULER;
    private static final Method GET_ASYNC_SCHEDULER;
    private static final Method GET_REGION_SCHEDULER;
    private static final Method ENTITY_GET_SCHEDULER;

    private static final Method GLOBAL_RUN;
    private static final Method GLOBAL_RUN_DELAYED;
    private static final Method GLOBAL_RUN_FIXED;

    private static final Method ASYNC_RUN;
    private static final Method ASYNC_RUN_DELAYED;
    private static final Method ASYNC_RUN_FIXED;

    private static final Method REGION_EXECUTE;

    private static final Method ENTITY_RUN;
    private static final Method ENTITY_RUN_DELAYED;
    private static final Method ENTITY_RUN_FIXED;

    private static final Method TASK_CANCEL;

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
                Class<?> server = Bukkit.getServer().getClass();
                GET_GLOBAL_SCHEDULER = server.getMethod("getGlobalRegionScheduler");
                GET_ASYNC_SCHEDULER = server.getMethod("getAsyncScheduler");
                GET_REGION_SCHEDULER = server.getMethod("getRegionScheduler");
                ENTITY_GET_SCHEDULER = Entity.class.getMethod("getScheduler");

                Class<?> globalCls = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Class<?> asyncCls = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                Class<?> regionCls = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
                Class<?> entityCls = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Class<?> taskCls = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

                GLOBAL_RUN = globalCls.getMethod("run", Plugin.class, Consumer.class);
                GLOBAL_RUN_DELAYED = globalCls.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                GLOBAL_RUN_FIXED = globalCls.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

                ASYNC_RUN = asyncCls.getMethod("runNow", Plugin.class, Consumer.class);
                ASYNC_RUN_DELAYED = asyncCls.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
                ASYNC_RUN_FIXED = asyncCls.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                REGION_EXECUTE = regionCls.getMethod("execute", Plugin.class, org.bukkit.World.class, int.class, int.class, Runnable.class);

                ENTITY_RUN = entityCls.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                ENTITY_RUN_DELAYED = entityCls.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                ENTITY_RUN_FIXED = entityCls.getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

                TASK_CANCEL = taskCls.getMethod("cancel");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to initialize Folia scheduler bridge", e);
            }
        } else {
            GET_GLOBAL_SCHEDULER = null;
            GET_ASYNC_SCHEDULER = null;
            GET_REGION_SCHEDULER = null;
            ENTITY_GET_SCHEDULER = null;
            GLOBAL_RUN = null;
            GLOBAL_RUN_DELAYED = null;
            GLOBAL_RUN_FIXED = null;
            ASYNC_RUN = null;
            ASYNC_RUN_DELAYED = null;
            ASYNC_RUN_FIXED = null;
            REGION_EXECUTE = null;
            ENTITY_RUN = null;
            ENTITY_RUN_DELAYED = null;
            ENTITY_RUN_FIXED = null;
            TASK_CANCEL = null;
        }
    }

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /** Runs on the next global region tick (Folia) or main thread tick (Bukkit). */
    public static Task run(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return foliaTask(GLOBAL_RUN, globalScheduler(), plugin, asConsumer(task));
        }
        return new Task(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static Task runLater(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            return foliaTask(GLOBAL_RUN_DELAYED, globalScheduler(), plugin, asConsumer(task), foliaDelay(delayTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static Task runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return foliaTask(GLOBAL_RUN_FIXED, globalScheduler(), plugin, asConsumer(task),
                    foliaDelay(delayTicks), Math.max(1L, periodTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    public static Task runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return foliaTask(ASYNC_RUN, asyncScheduler(), plugin, asConsumer(task));
        }
        return new Task(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    public static Task runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            return foliaTask(ASYNC_RUN_DELAYED, asyncScheduler(), plugin, asConsumer(task),
                    foliaDelay(delayTicks) * 50L, TimeUnit.MILLISECONDS);
        }
        return new Task(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    public static Task runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return foliaTask(ASYNC_RUN_FIXED, asyncScheduler(), plugin, asConsumer(task),
                    foliaDelay(delayTicks) * 50L, Math.max(1L, periodTicks) * 50L, TimeUnit.MILLISECONDS);
        }
        return new Task(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    /** Runs on the entity's region tick (Folia) or main thread (Bukkit). */
    public static Task runForEntity(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            return foliaTask(ENTITY_RUN, entityScheduler(entity), plugin, asConsumer(task), null);
        }
        return new Task(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static Task runForEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (FOLIA) {
            return foliaTask(ENTITY_RUN_DELAYED, entityScheduler(entity), plugin, asConsumer(task), null, foliaDelay(delayTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static Task runForEntityTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return foliaTask(ENTITY_RUN_FIXED, entityScheduler(entity), plugin, asConsumer(task), null,
                    foliaDelay(delayTicks), Math.max(1L, periodTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    /** Folia: dispatches to the global region. Paper/Spigot: runs inline. */
    public static void runOrNow(Plugin plugin, Runnable task) {
        if (FOLIA) {
            invoke(GLOBAL_RUN, globalScheduler(), plugin, asConsumer(task));
        } else {
            task.run();
        }
    }

    /** Folia: dispatches to the entity's region. Paper/Spigot: runs inline. */
    public static void runForEntityOrNow(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            invoke(ENTITY_RUN, entityScheduler(entity), plugin, asConsumer(task), null);
        } else {
            task.run();
        }
    }

    /** Runs on the region that owns the given location. */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            invoke(REGION_EXECUTE, regionScheduler(), plugin, location.getWorld(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
        } else {
            task.run();
        }
    }

    private static Object globalScheduler() {
        return invoke(GET_GLOBAL_SCHEDULER, Bukkit.getServer());
    }

    private static Object asyncScheduler() {
        return invoke(GET_ASYNC_SCHEDULER, Bukkit.getServer());
    }

    private static Object regionScheduler() {
        return invoke(GET_REGION_SCHEDULER, Bukkit.getServer());
    }

    private static Object entityScheduler(Entity entity) {
        return invoke(ENTITY_GET_SCHEDULER, entity);
    }

    private static Task foliaTask(Method method, Object target, Object... args) {
        return new Task(invoke(method, target, args));
    }

    private static Consumer<Object> asConsumer(Runnable runnable) {
        return ignored -> runnable.run();
    }

    /** Folia's delayed/timer APIs reject delays of 0; clamp to 1 tick. */
    private static long foliaDelay(long ticks) {
        return Math.max(1L, ticks);
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
                TASK_CANCEL.invoke(handle);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to cancel Folia task", e);
            }
        }
    }
}
