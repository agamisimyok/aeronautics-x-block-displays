package tr.agasis.aebd.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import tr.agasis.aebd.aeronautics.SableBridge;
import tr.agasis.aebd.aeronauticsblockdisplays;
import tr.agasis.aebd.common.DisplayKind;
import tr.agasis.aebd.common.DisplayXform;
import tr.agasis.aebd.net.AEBDPayloads;

/** Supplies an exact per-frame render correction while the server remains authoritative. */
public final class ClientAttachmentManager {

    private static final Map<Integer, AEBDPayloads.AttachmentSync> ATTACHMENTS = new HashMap<>();

    public static void sync(AEBDPayloads.AttachmentSync payload) {
        if (payload.attached()) {
            ATTACHMENTS.put(payload.entityId(), payload);
        } else {
            ATTACHMENTS.remove(payload.entityId());
        }
    }

    public static void applyRenderTransform(Display display, float partialTick, PoseStack poseStack) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            ATTACHMENTS.clear();
            return;
        }
        AEBDPayloads.AttachmentSync attachment = ATTACHMENTS.get(display.getId());
        DisplayKind kind = DisplayKind.of(display);
        Display.RenderState state = display.renderState();
        if (attachment == null || kind == null || state == null) {
            return;
        }
        Object subLevel = SableBridge.findClient(level, attachment.subLevelId());
        if (subLevel == null) {
            return;
        }
        try {
            DisplayXform local = attachment.localXform();
            Vec3 desiredCenter = SableBridge.toRenderWorld(subLevel, local.center(), partialTick);
            Quaternionf desiredLeft = SableBridge.renderOrientation(subLevel, partialTick)
                    .mul(new Quaternionf(local.left()));
            DisplayXform desired = new DisplayXform(
                    desiredCenter, desiredLeft, local.scale(), local.right());

            Vec3 vanillaCenter = display.getPosition(partialTick);
            poseStack.translate(desiredCenter.x - vanillaCenter.x,
                    desiredCenter.y - vanillaCenter.y, desiredCenter.z - vanillaCenter.z);

            float progress = display.calculateInterpolationProgress(partialTick);
            Transformation current = state.transformation().get(progress);
            Matrix4f billboard = billboardMatrix(state.billboardConstraints(), display, partialTick);
            Matrix4f currentFull = new Matrix4f(billboard).mul(current.getMatrix());
            Matrix4f desiredFull = new Matrix4f(billboard).mul(desired.toTransformation(kind).getMatrix());
            Matrix4f correction = desiredFull.mul(currentFull.invert());
            if (correction.isFinite()) {
                poseStack.mulPose(correction);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ATTACHMENTS.remove(display.getId());
            aeronauticsblockdisplays.LOGGER.error("Failed to apply client attachment render matrix", exception);
        }
    }

    private static Matrix4f billboardMatrix(Display.BillboardConstraints constraint, Display display, float partialTick) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float entityY = Mth.rotLerp(partialTick, display.yRotO, display.getYRot());
        float entityX = Mth.lerp(partialTick, display.xRotO, display.getXRot());
        Quaternionf rotation = switch (constraint) {
            case FIXED -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * entityY,
                    (float) (Math.PI / 180.0) * entityX, 0.0f);
            case HORIZONTAL -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * entityY,
                    (float) (Math.PI / 180.0) * -camera.getXRot(), 0.0f);
            case VERTICAL -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * (camera.getYRot() - 180.0f),
                    (float) (Math.PI / 180.0) * entityX, 0.0f);
            case CENTER -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * (camera.getYRot() - 180.0f),
                    (float) (Math.PI / 180.0) * -camera.getXRot(), 0.0f);
        };
        return new Matrix4f().rotation(rotation);
    }

    private ClientAttachmentManager() {}
}
