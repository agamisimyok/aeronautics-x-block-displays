package tr.agasis.aebd.aeronautics;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tr.agasis.aebd.common.DisplayXform;

/** Persistent, server-authoritative pose of a display relative to a Sable sub-level. */
public record DisplayAttachment(UUID subLevelId, DisplayXform localXform) {

    private static final String TAG = "aebd_sub_level_attachment";

    public void save(Display display) {
        CompoundTag data = new CompoundTag();
        data.putUUID("sub_level", this.subLevelId);
        writeVec3(data, "center", this.localXform.center());
        writeQuaternion(data, "left", this.localXform.left());
        writeVector(data, "scale", this.localXform.scale());
        writeQuaternion(data, "right", this.localXform.right());
        display.getPersistentData().put(TAG, data);
    }

    @Nullable
    public static DisplayAttachment load(Display display) {
        CompoundTag root = display.getPersistentData();
        if (!root.contains(TAG)) {
            return null;
        }
        CompoundTag data = root.getCompound(TAG);
        if (!data.hasUUID("sub_level")) {
            return null;
        }
        return new DisplayAttachment(data.getUUID("sub_level"), new DisplayXform(
                readVec3(data, "center"),
                readQuaternion(data, "left"),
                readVector(data, "scale"),
                readQuaternion(data, "right")));
    }

    public static void clear(Display display) {
        display.getPersistentData().remove(TAG);
    }

    private static void writeVec3(CompoundTag tag, String key, Vec3 value) {
        tag.putDouble(key + "_x", value.x);
        tag.putDouble(key + "_y", value.y);
        tag.putDouble(key + "_z", value.z);
    }

    private static Vec3 readVec3(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "_x"), tag.getDouble(key + "_y"), tag.getDouble(key + "_z"));
    }

    private static void writeQuaternion(CompoundTag tag, String key, Quaternionf value) {
        tag.putFloat(key + "_x", value.x);
        tag.putFloat(key + "_y", value.y);
        tag.putFloat(key + "_z", value.z);
        tag.putFloat(key + "_w", value.w);
    }

    private static Quaternionf readQuaternion(CompoundTag tag, String key) {
        return new Quaternionf(tag.getFloat(key + "_x"), tag.getFloat(key + "_y"),
                tag.getFloat(key + "_z"), tag.getFloat(key + "_w"));
    }

    private static void writeVector(CompoundTag tag, String key, Vector3f value) {
        tag.putFloat(key + "_x", value.x);
        tag.putFloat(key + "_y", value.y);
        tag.putFloat(key + "_z", value.z);
    }

    private static Vector3f readVector(CompoundTag tag, String key) {
        return new Vector3f(tag.getFloat(key + "_x"), tag.getFloat(key + "_y"), tag.getFloat(key + "_z"));
    }
}
