package tr.agasis.aebd.aeronautics;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tr.agasis.aebd.common.DisplayKind;
import tr.agasis.aebd.common.DisplayOps;
import tr.agasis.aebd.common.DisplayXform;
import tr.agasis.aebd.net.AEBDPayloads;

/** Attaches manually edited displays and advances attached poses once per server level tick. */
public final class AttachmentManager {

    public static void afterManualTransform(ServerLevel level, Display display, DisplayKind kind, DisplayXform xform) {
        if (!SableBridge.isAvailable()) {
            return;
        }
        try {
            AABB bounds = boundsOf(xform, kind).inflate(0.02);
            Object subLevel = SableBridge.findIntersecting(level, bounds);
            if (subLevel == null) {
                subLevel = inheritedSubLevel(level, display, bounds);
            }
            if (subLevel == null) {
                DisplayAttachment.clear(display);
                sync(display, null);
                return;
            }

            Vec3 localCenter = SableBridge.toLocal(subLevel, xform.center());
            Quaternionf localLeft = SableBridge.orientation(subLevel).conjugate().mul(new Quaternionf(xform.left()));
            DisplayAttachment attachment = new DisplayAttachment(SableBridge.id(subLevel), new DisplayXform(
                    localCenter, localLeft, new Vector3f(xform.scale()), new Quaternionf(xform.right())));
            attachment.save(display);
            sync(display, attachment);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SableBridge.reportFailure(exception);
        }
    }

    private static void sync(Display display, @Nullable DisplayAttachment attachment) {
        DisplayXform local = attachment == null ? DisplayXform.atCenter(Vec3.ZERO) : attachment.localXform();
        java.util.UUID id = attachment == null ? new java.util.UUID(0L, 0L) : attachment.subLevelId();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(display,
                new AEBDPayloads.AttachmentSync(display.getId(), attachment != null, id, local));
    }

    /** Re-sends persistent attachment state whenever a client starts tracking a loaded display. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Display display)) {
            return;
        }
        DisplayAttachment attachment = DisplayAttachment.load(display);
        if (attachment != null) {
            PacketDistributor.sendToPlayer(player, new AEBDPayloads.AttachmentSync(
                    display.getId(), true, attachment.subLevelId(), attachment.localXform()));
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !SableBridge.isAvailable()) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Display display)) {
                continue;
            }
            DisplayAttachment attachment = DisplayAttachment.load(display);
            DisplayKind kind = DisplayKind.of(display);
            if (attachment == null || kind == null) {
                continue;
            }
            Object subLevel = SableBridge.find(level, attachment.subLevelId());
            if (subLevel == null) {
                DisplayAttachment.clear(display);
                continue;
            }
            try {
                DisplayXform local = attachment.localXform();
                Vec3 center = SableBridge.toWorld(subLevel, local.center());
                Quaternionf left = SableBridge.orientation(subLevel).mul(new Quaternionf(local.left()));
                DisplayOps.applyFollowingXform(display, kind, new DisplayXform(
                        center, left, new Vector3f(local.scale()), new Quaternionf(local.right())));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                SableBridge.reportFailure(exception);
                return;
            }
        }
    }

    @Nullable
    private static Object inheritedSubLevel(ServerLevel level, Display moved, AABB movedBounds)
            throws ReflectiveOperationException {
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Display other) || other == moved) {
                continue;
            }
            DisplayAttachment otherAttachment = DisplayAttachment.load(other);
            DisplayKind otherKind = DisplayKind.of(other);
            if (otherAttachment == null || otherKind == null) {
                continue;
            }
            Object subLevel = SableBridge.find(level, otherAttachment.subLevelId());
            if (subLevel == null) {
                continue;
            }
            DisplayXform local = otherAttachment.localXform();
            DisplayXform otherXform = new DisplayXform(
                    SableBridge.toWorld(subLevel, local.center()),
                    SableBridge.orientation(subLevel).mul(new Quaternionf(local.left())),
                    new Vector3f(local.scale()),
                    new Quaternionf(local.right()));
            if (movedBounds.intersects(boundsOf(otherXform, otherKind).inflate(0.02))) {
                return subLevel;
            }
        }
        return null;
    }

    private static AABB boundsOf(DisplayXform xform, DisplayKind kind) {
        Matrix4f matrix = xform.worldMatrix(kind);
        Vector3f min = kind.localMin();
        Vector3f max = kind.localMax();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    Vector3f corner = new Vector3f(x == 0 ? min.x : max.x,
                            y == 0 ? min.y : max.y, z == 0 ? min.z : max.z);
                    matrix.transformPosition(corner);
                    minX = Math.min(minX, corner.x);
                    minY = Math.min(minY, corner.y);
                    minZ = Math.min(minZ, corner.z);
                    maxX = Math.max(maxX, corner.x);
                    maxY = Math.max(maxY, corner.y);
                    maxZ = Math.max(maxZ, corner.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private AttachmentManager() {}
}
