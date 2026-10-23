package tr.agasis.aebd.aeronautics;

import java.lang.reflect.Method;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import tr.agasis.aebd.aeronauticsblockdisplays;

/** Keeps every Sable API reference behind a runtime mod check so AEBD remains standalone. */
public final class SableBridge {

    private static boolean initialized;
    private static boolean available;
    private static boolean failureLogged;
    private static Method getContainer;
    private static Method getClientContainer;
    private static Method getAllSubLevels;
    private static Method getSubLevel;
    private static Method getUniqueId;
    private static Method boundingBox;
    private static Method intersects;
    private static Method logicalPose;
    private static Method transformPosition;
    private static Method transformPositionInverse;
    private static Method orientation;
    private static Method quaternionX;
    private static Method quaternionY;
    private static Method quaternionZ;
    private static Method quaternionW;

    static boolean isAvailable() {
        initialize();
        return available;
    }

    @Nullable
    static Object findIntersecting(ServerLevel level, AABB worldBounds) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Object container = getContainer.invoke(null, level);
            Iterable<?> subLevels = (Iterable<?>) getAllSubLevels.invoke(container);
            for (Object subLevel : subLevels) {
                Object bounds = boundingBox.invoke(subLevel);
                if ((boolean) intersects.invoke(bounds, worldBounds)) {
                    return subLevel;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disable(exception);
        }
        return null;
    }

    @Nullable
    static Object find(ServerLevel level, UUID id) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Object container = getContainer.invoke(null, level);
            return getSubLevel.invoke(container, id);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disable(exception);
            return null;
        }
    }

    @Nullable
    public static Object findClient(ClientLevel level, UUID id) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Object container = getClientContainer.invoke(null, level);
            return getSubLevel.invoke(container, id);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disable(exception);
            return null;
        }
    }

    static UUID id(Object subLevel) throws ReflectiveOperationException {
        return (UUID) getUniqueId.invoke(subLevel);
    }

    static Vec3 toLocal(Object subLevel, Vec3 worldPosition) throws ReflectiveOperationException {
        Object pose = logicalPose.invoke(subLevel);
        return (Vec3) transformPositionInverse.invoke(pose, worldPosition);
    }

    static Vec3 toWorld(Object subLevel, Vec3 localPosition) throws ReflectiveOperationException {
        Object pose = logicalPose.invoke(subLevel);
        return (Vec3) transformPosition.invoke(pose, localPosition);
    }

    public static Vec3 toRenderWorld(Object subLevel, Vec3 localPosition, float partialTick)
            throws ReflectiveOperationException {
        Method renderPose = subLevel.getClass().getMethod("renderPose", float.class);
        Object pose = renderPose.invoke(subLevel, partialTick);
        return (Vec3) transformPosition.invoke(pose, localPosition);
    }

    static Quaternionf orientation(Object subLevel) throws ReflectiveOperationException {
        Object pose = logicalPose.invoke(subLevel);
        Object quaternion = orientation.invoke(pose);
        return new Quaternionf(
                ((Number) quaternionX.invoke(quaternion)).floatValue(),
                ((Number) quaternionY.invoke(quaternion)).floatValue(),
                ((Number) quaternionZ.invoke(quaternion)).floatValue(),
                ((Number) quaternionW.invoke(quaternion)).floatValue());
    }

    public static Quaternionf renderOrientation(Object subLevel, float partialTick)
            throws ReflectiveOperationException {
        Method renderPose = subLevel.getClass().getMethod("renderPose", float.class);
        Object pose = renderPose.invoke(subLevel, partialTick);
        Object quaternion = orientation.invoke(pose);
        return new Quaternionf(
                ((Number) quaternionX.invoke(quaternion)).floatValue(),
                ((Number) quaternionY.invoke(quaternion)).floatValue(),
                ((Number) quaternionZ.invoke(quaternion)).floatValue(),
                ((Number) quaternionW.invoke(quaternion)).floatValue());
    }

    static void reportFailure(Exception exception) {
        disable(exception);
    }

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded("sable")) {
            return;
        }
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> boundsClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            Class<?> quaternionClass = Class.forName("org.joml.Quaterniondc");
            getContainer = containerClass.getMethod("getContainer", ServerLevel.class);
            getClientContainer = containerClass.getMethod("getContainer", ClientLevel.class);
            getAllSubLevels = containerClass.getMethod("getAllSubLevels");
            getSubLevel = containerClass.getMethod("getSubLevel", UUID.class);
            getUniqueId = subLevelClass.getMethod("getUniqueId");
            boundingBox = subLevelClass.getMethod("boundingBox");
            intersects = boundsClass.getMethod("intersects", AABB.class);
            logicalPose = subLevelClass.getMethod("logicalPose");
            transformPosition = poseClass.getMethod("transformPosition", Vec3.class);
            transformPositionInverse = poseClass.getMethod("transformPositionInverse", Vec3.class);
            orientation = poseClass.getMethod("orientation");
            quaternionX = quaternionClass.getMethod("x");
            quaternionY = quaternionClass.getMethod("y");
            quaternionZ = quaternionClass.getMethod("z");
            quaternionW = quaternionClass.getMethod("w");
            available = true;
            aeronauticsblockdisplays.LOGGER.info("Sable sub-level attachment support enabled");
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private static void disable(Throwable exception) {
        available = false;
        if (!failureLogged) {
            failureLogged = true;
            aeronauticsblockdisplays.LOGGER.error("Disabling Sable attachment support: incompatible Sable API", exception);
        }
    }

    private SableBridge() {}
}
