package com.misp.mspellcompat.sable;

import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SableSublevelActivity {
    private static final Map<Class<?>, Method> BOUNDS_METHODS = new ConcurrentHashMap<>();
    private static final Map<BoundsMethodKey, Method> COORDINATE_METHODS = new ConcurrentHashMap<>();

    private SableSublevelActivity() {
    }

    public static boolean hasNearbyPlayer(ServerLevel level, Object sublevel) {
        if (level == null || sublevel == null || level.players().isEmpty()) {
            return false;
        }

        int radius = SableFluidRuntimeConfig.PLAYER_ACTIVATION_RADIUS.get();
        WorldBounds bounds = worldBounds(sublevel);
        if (bounds == null) {
            return false;
        }

        double radiusSquared = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            double dx = distanceToAxis(player.getX(), bounds.minX(), bounds.maxX());
            double dy = distanceToAxis(player.getY(), bounds.minY(), bounds.maxY());
            double dz = distanceToAxis(player.getZ(), bounds.minZ(), bounds.maxZ());
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static WorldBounds worldBounds(Object sublevel) {
        try {
            Class<?> type = sublevel.getClass();
            Method method = BOUNDS_METHODS.get(type);
            if (method == null) {
                Method found = type.getMethod("boundingBox");
                Method previous = BOUNDS_METHODS.putIfAbsent(type, found);
                method = previous == null ? found : previous;
            }
            Object box = method.invoke(sublevel);
            if (box == null) return null;
            return new WorldBounds(
                    readCoordinate(box, "minX"), readCoordinate(box, "minY"), readCoordinate(box, "minZ"),
                    readCoordinate(box, "maxX"), readCoordinate(box, "maxY"), readCoordinate(box, "maxZ"));
        } catch (Throwable failure) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.error("sable_activity", "bounds_lookup_failed", failure,
                        "sublevelType=" + sublevel.getClass().getName());
            }
        }
        return null;
    }

    private static double readCoordinate(Object box, String name) throws ReflectiveOperationException {
        BoundsMethodKey key = new BoundsMethodKey(box.getClass(), name);
        Method method = COORDINATE_METHODS.get(key);
        if (method == null) {
            Method found = box.getClass().getMethod(name);
            Method previous = COORDINATE_METHODS.putIfAbsent(key, found);
            method = previous == null ? found : previous;
        }
        Object value = method.invoke(box);
        if (value instanceof Number number) return number.doubleValue();
        throw new ReflectiveOperationException(name + "() did not return a number");
    }

    private static double distanceToAxis(double coordinate, double min, double max) {
        if (coordinate < min) return min - coordinate;
        if (coordinate > max) return coordinate - max;
        return 0.0;
    }

    private record BoundsMethodKey(Class<?> owner, String name) {
    }

    private record WorldBounds(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
    }
}
