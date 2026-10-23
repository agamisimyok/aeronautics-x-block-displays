package tr.agasis.aebd.client;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tr.agasis.aebd.aeronauticsblockdisplays;
import tr.agasis.aebd.common.DisplayKind;
import tr.agasis.aebd.common.DisplayXform;

/** Draws selection and hover outlines, plus the active axis guide. */
@EventBusSubscriber(modid = aeronauticsblockdisplays.MODID, value = Dist.CLIENT)
public final class EditorRenderer {

    private static final int SELECTED = 0xFF4FC3F7;
    private static final int HOVERED = 0xFFFFFFFF;
    private static final int AXIS_X = 0xFFFF5555;
    private static final int AXIS_Y = 0xFF55FF55;
    private static final int AXIS_Z = 0xFF5599FF;
    private static final double AXIS_GUIDE_LENGTH = 24.0;
    private static final float PIVOT_MARKER = 0.25f;

    /**
     * Line type for the gizmo: identical to vanilla {@code lines()} but with the depth test disabled
     * and depth writes off, so the manipulator stays visible even when it sits inside the display.
     */
    private static final RenderType GIZMO_LINES = RenderType.create(
            "aebd_gizmo_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !EditorSession.active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        EditorSession session = EditorSession.INSTANCE;
        if (session.selection().isEmpty() && session.hovered() < 0) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = pose.last().pose();

        int hovered = session.hovered();
        if (hovered >= 0 && !session.selection().contains(hovered)) {
            drawOutline(lines, matrix, mc.level, session, hovered, partialTick, HOVERED);
        }
        for (int id : session.selection()) {
            drawOutline(lines, matrix, mc.level, session, id, partialTick, SELECTED);
        }

        boolean hasGizmo = session.gizmoMode() != GizmoMode.NONE && !session.selection().isEmpty();
        if (hasGizmo) {
            // The gizmo draws on its own see-through pass so it shows through the display it surrounds.
            drawGizmo(buffers.getBuffer(GIZMO_LINES), matrix, session, camera);
        } else {
            drawAxisGuide(lines, matrix, session);
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
        if (hasGizmo) {
            buffers.endBatch(GIZMO_LINES);
        }
    }

    private static void drawOutline(VertexConsumer consumer, Matrix4f matrix, Level level,
            EditorSession session, int entityId, float partialTick, int color) {
        Display display = ClientDisplays.displayById(level, entityId);
        DisplayKind kind = DisplayKind.of(display);
        if (kind == null) {
            return;
        }
        // The predicted transform beats the entity's own while the server catches up.
        DisplayXform xform = session.previewOf(entityId);
        if (xform == null) {
            xform = ClientDisplays.xformOf(display, kind, partialTick);
        }
        if (xform == null) {
            return;
        }
        Vector3f[] corners = ClientDisplays.corners(xform, kind);
        for (int from = 0; from < 8; from++) {
            for (int bit = 0; bit < 3; bit++) {
                int mask = 1 << bit;
                if ((from & mask) == 0) {
                    line(consumer, matrix, corners[from], corners[from | mask], color);
                }
            }
        }
    }

    private static void drawAxisGuide(VertexConsumer consumer, Matrix4f matrix, EditorSession session) {
        if (session.selection().isEmpty()) {
            return;
        }
        Vec3 center = EditorSession.centerOfXforms(session.currentXforms().values());
        if (session.axis() == EditorSession.Axis.FREE) {
            // No constraint, so just mark the pivot everything would rotate about.
            marker(consumer, matrix, center, new Vec3(1.0, 0.0, 0.0), PIVOT_MARKER, AXIS_X);
            marker(consumer, matrix, center, new Vec3(0.0, 1.0, 0.0), PIVOT_MARKER, AXIS_Y);
            marker(consumer, matrix, center, new Vec3(0.0, 0.0, 1.0), PIVOT_MARKER, AXIS_Z);
            return;
        }
        Vec3 direction = session.axis().vector();
        int color = switch (session.axis()) {
            case X -> AXIS_X;
            case Y -> AXIS_Y;
            case Z -> AXIS_Z;
            case FREE -> SELECTED;
        };
        marker(consumer, matrix, center, direction, AXIS_GUIDE_LENGTH, color);
    }

    private static final int[] AXIS_COLORS = {AXIS_X, AXIS_Y, AXIS_Z};
    private static final int HANDLE_HOT = 0xFFFFDD33;
    private static final int RING_SEGMENTS = 48;

    private static void drawGizmo(VertexConsumer consumer, Matrix4f matrix, EditorSession session, Vec3 camera) {
        Vec3 center = session.gizmoCenter();
        double size = session.gizmoSize(camera);
        int hovered = session.hoveredHandle();
        switch (session.gizmoMode()) {
            case MOVE -> {
                for (int axis = 0; axis < 3; axis++) {
                    int color = hovered == axis ? HANDLE_HOT : AXIS_COLORS[axis];
                    marker(consumer, matrix, center, session.gizmoAxisVector(axis), size, color, false);
                    drawArrowHead(consumer, matrix, center, size, axis, color, session);
                }
            }
            case SCALE -> {
                for (int axis = 0; axis < 3; axis++) {
                    boolean hot = hovered == axis;
                    int color = hot ? HANDLE_HOT : AXIS_COLORS[axis];
                    Vec3 end = center.add(session.gizmoAxisVector(axis).scale(size));
                    line(consumer, matrix, vec(center), vec(end), color);
                    drawBox(consumer, matrix, end, size * 0.08, color);
                }
                drawBox(consumer, matrix, center, size * 0.09, hovered == Gizmo.UNIFORM ? HANDLE_HOT : 0xFFDDDDDD);
            }
            case ROTATE -> {
                for (int axis = 0; axis < 3; axis++) {
                    drawRing(consumer, matrix, center, axis, size,
                            session,
                            hovered == axis ? HANDLE_HOT : AXIS_COLORS[axis]);
                }
            }
            case NONE -> {
            }
        }
    }

    private static void drawArrowHead(VertexConsumer consumer, Matrix4f matrix, Vec3 center,
            double size, int axis, int color, EditorSession session) {
        Vec3 direction = session.gizmoAxisVector(axis);
        Vec3 tip = center.add(direction.scale(size));
        Vec3 base = tip.subtract(direction.scale(size * 0.22));
        Vec3 u = session.gizmoAxisVector((axis + 1) % 3).scale(size * 0.09);
        Vec3 v = session.gizmoAxisVector((axis + 2) % 3).scale(size * 0.09);
        line(consumer, matrix, vec(tip), vec(base.add(u)), color);
        line(consumer, matrix, vec(tip), vec(base.subtract(u)), color);
        line(consumer, matrix, vec(tip), vec(base.add(v)), color);
        line(consumer, matrix, vec(tip), vec(base.subtract(v)), color);
    }

    private static void drawPlaneHandle(VertexConsumer consumer, Matrix4f matrix, Vec3 center,
            double size, int normal, int color) {
        Vec3 u = Gizmo.axisVec((normal + 1) % 3);
        Vec3 v = Gizmo.axisVec((normal + 2) % 3);
        double lo = size * 0.22;
        double hi = size * 0.5;
        Vec3 c00 = center.add(u.scale(lo)).add(v.scale(lo));
        Vec3 c10 = center.add(u.scale(hi)).add(v.scale(lo));
        Vec3 c11 = center.add(u.scale(hi)).add(v.scale(hi));
        Vec3 c01 = center.add(u.scale(lo)).add(v.scale(hi));
        line(consumer, matrix, vec(c00), vec(c10), color);
        line(consumer, matrix, vec(c10), vec(c11), color);
        line(consumer, matrix, vec(c11), vec(c01), color);
        line(consumer, matrix, vec(c01), vec(c00), color);
    }

    private static void drawRing(VertexConsumer consumer, Matrix4f matrix, Vec3 center,
            int normal, double radius, EditorSession session, int color) {
        Vec3 u = session.gizmoAxisVector((normal + 1) % 3);
        Vec3 v = session.gizmoAxisVector((normal + 2) % 3);
        Vector3f previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0 * i) / RING_SEGMENTS;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;
            Vec3 point = center.add(u.scale(cos)).add(v.scale(sin));
            Vector3f current = vec(point);
            if (previous != null) {
                line(consumer, matrix, previous, current, color);
            }
            previous = current;
        }
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix, Vec3 center, double half, int color) {
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f(
                    (float) (center.x + ((i & 1) == 0 ? -half : half)),
                    (float) (center.y + ((i & 2) == 0 ? -half : half)),
                    (float) (center.z + ((i & 4) == 0 ? -half : half)));
        }
        for (int from = 0; from < 8; from++) {
            for (int bit = 0; bit < 3; bit++) {
                int mask = 1 << bit;
                if ((from & mask) == 0) {
                    line(consumer, matrix, corners[from], corners[from | mask], color);
                }
            }
        }
    }

    private static Vector3f vec(Vec3 point) {
        return new Vector3f((float) point.x, (float) point.y, (float) point.z);
    }

    private static void marker(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 direction,
            double halfLength, int color, boolean twoSided) {
        Vec3 from = twoSided ? center.subtract(direction.scale(halfLength)) : center;
        Vec3 to = center.add(direction.scale(halfLength));
        line(consumer, matrix,
                new Vector3f((float) from.x, (float) from.y, (float) from.z),
                new Vector3f((float) to.x, (float) to.y, (float) to.z),
                color);
    }

    private static void marker(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 direction,
            double halfLength, int color) {
        Vec3 from = center.subtract(direction.scale(halfLength));
        Vec3 to = center.add(direction.scale(halfLength));
        line(consumer, matrix,
                new Vector3f((float) from.x, (float) from.y, (float) from.z),
                new Vector3f((float) to.x, (float) to.y, (float) to.z),
                color);
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, Vector3f from, Vector3f to, int argb) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        int alpha = argb >>> 24;
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        consumer.addVertex(matrix, from.x, from.y, from.z).setColor(red, green, blue, alpha).setNormal(dx, dy, dz);
        consumer.addVertex(matrix, to.x, to.y, to.z).setColor(red, green, blue, alpha).setNormal(dx, dy, dz);
    }

    private EditorRenderer() {}
}
