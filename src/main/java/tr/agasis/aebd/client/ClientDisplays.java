package tr.agasis.aebd.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.math.Transformation;

import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tr.agasis.aebd.common.DisplayData;
import tr.agasis.aebd.common.DisplayKind;
import tr.agasis.aebd.common.DisplayProps;
import tr.agasis.aebd.common.DisplayXform;

/**
 * Reads display state back out of the client's entity copies.
 *
 * <p>Vanilla's display getters are private, but {@link Display#renderState()} is public and carries
 * everything the renderer needs, which is also everything the editor needs.
 */
public final class ClientDisplays {

    /** A display whose scale has collapsed on any axis has no invertible matrix, so it can't be hit. */
    private static final float MIN_SCALE = 1.0E-5f;

    @Nullable
    public static DisplayXform xformOf(Display display, DisplayKind kind, float partialTick) {
        Display.RenderState state = display.renderState();
        if (state == null) {
            return null;
        }
        Transformation transformation = state.transformation().get(partialTick);
        return DisplayXform.from(display.position(), transformation, kind);
    }

    @Nullable
    public static DisplayXform xformOf(Display display) {
        DisplayKind kind = DisplayKind.of(display);
        return kind == null ? null : xformOf(display, kind, 1.0f);
    }

    public static DisplayProps propsOf(Display display) {
        Display.RenderState state = display.renderState();
        if (state == null) {
            return DisplayProps.DEFAULT;
        }
        int block = -1;
        int sky = -1;
        int packed = state.brightnessOverride();
        if (packed != -1) {
            Brightness brightness = Brightness.unpack(packed);
            block = brightness.block();
            sky = brightness.sky();
        }
        byte itemTransform = DisplayProps.DEFAULT.itemTransform();
        if (display instanceof Display.ItemDisplay itemDisplay) {
            Display.ItemDisplay.ItemRenderState itemState = itemDisplay.itemRenderState();
            if (itemState != null) {
                itemTransform = itemState.itemTransform().getId();
            }
        }
        return new DisplayProps(
                (byte) state.billboardConstraints().ordinal(),
                block,
                sky,
                // View range is not part of the render state, so it can only be written, not read.
                DisplayProps.DEFAULT.viewRange(),
                state.shadowRadius().get(1.0f),
                state.shadowStrength().get(1.0f),
                state.glowColorOverride(),
                itemTransform);
    }

    @Nullable
    public static DisplayData dataOf(Display display) {
        DisplayKind kind = DisplayKind.of(display);
        if (kind == null) {
            return null;
        }
        DisplayXform xform = xformOf(display, kind, 1.0f);
        if (xform == null) {
            return null;
        }
        DisplayProps props = propsOf(display);
        if (kind == DisplayKind.BLOCK) {
            Display.BlockDisplay blockDisplay = (Display.BlockDisplay) display;
            Display.BlockDisplay.BlockRenderState blockState = blockDisplay.blockRenderState();
            BlockState state = blockState == null ? Blocks.AIR.defaultBlockState() : blockState.blockState();
            return new DisplayData(kind, state, ItemStack.EMPTY, xform, props);
        }
        Display.ItemDisplay itemDisplay = (Display.ItemDisplay) display;
        Display.ItemDisplay.ItemRenderState itemState = itemDisplay.itemRenderState();
        ItemStack stack = itemState == null ? ItemStack.EMPTY : itemState.itemStack().copy();
        return new DisplayData(kind, Blocks.AIR.defaultBlockState(), stack, xform, props);
    }

    @Nullable
    public static Display displayById(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof Display display ? display : null;
    }

    public static List<Display> nearby(Level level, Vec3 around, double radius) {
        AABB search = new AABB(around, around).inflate(radius);
        List<Display> found = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, search, candidate -> DisplayKind.of(candidate) != null)) {
            found.add((Display) entity);
        }
        return found;
    }

    /** The eight world-space corners of a display's render box, in the order the outline expects. */
    @Nullable
    public static Vector3f[] corners(Display display, float partialTick) {
        DisplayKind kind = DisplayKind.of(display);
        if (kind == null) {
            return null;
        }
        DisplayXform xform = xformOf(display, kind, partialTick);
        if (xform == null) {
            return null;
        }
        return corners(xform, kind);
    }

    public static Vector3f[] corners(DisplayXform xform, DisplayKind kind) {
        Matrix4f matrix = xform.worldMatrix(kind);
        Vector3f min = kind.localMin();
        Vector3f max = kind.localMax();
        Vector3f[] out = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            out[i] = matrix.transformPosition(new Vector3f(
                    (i & 1) == 0 ? min.x : max.x,
                    (i & 2) == 0 ? min.y : max.y,
                    (i & 4) == 0 ? min.z : max.z));
        }
        return out;
    }

    /**
     * Ray-casts against the transformed render boxes of nearby displays.
     *
     * @return the entity id of the closest hit display, or {@code -1}
     */
    public static int pick(Level level, Vec3 from, Vec3 direction, double maxDistance) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (Display display : nearby(level, from, maxDistance)) {
            DisplayKind kind = DisplayKind.of(display);
            DisplayXform xform = kind == null ? null : xformOf(display, kind, 1.0f);
            if (xform == null) {
                continue;
            }
            Vector3f scale = xform.scale();
            if (Math.abs(scale.x) < MIN_SCALE || Math.abs(scale.y) < MIN_SCALE || Math.abs(scale.z) < MIN_SCALE) {
                continue;
            }
            Matrix4f inverse = xform.worldMatrix(kind).invert();
            if (!inverse.isFinite()) {
                continue;
            }
            Vector3f origin = inverse.transformPosition(new Vector3f((float) from.x, (float) from.y, (float) from.z));
            Vector3f ray = inverse.transformDirection(
                    new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
            Float hit = intersectBox(origin, ray, kind.localMin(), kind.localMax());
            if (hit == null) {
                continue;
            }
            Vector3f localPoint = new Vector3f(ray).mul(hit).add(origin);
            Vector3f worldPoint = xform.worldMatrix(kind).transformPosition(localPoint);
            double distance = from.distanceToSqr(worldPoint.x, worldPoint.y, worldPoint.z);
            if (distance < bestDistance && distance <= maxDistance * maxDistance) {
                bestDistance = distance;
                best = display.getId();
            }
        }
        return best;
    }

    /** Slab test. Returns the entry distance along {@code ray}, or null when the box is missed. */
    @Nullable
    private static Float intersectBox(Vector3f origin, Vector3f ray, Vector3f min, Vector3f max) {
        float near = Float.NEGATIVE_INFINITY;
        float far = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float o = origin.get(axis);
            float d = ray.get(axis);
            float lo = min.get(axis);
            float hi = max.get(axis);
            if (Math.abs(d) < 1.0E-7f) {
                if (o < lo || o > hi) {
                    return null;
                }
                continue;
            }
            float t1 = (lo - o) / d;
            float t2 = (hi - o) / d;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return null;
            }
        }
        if (far < 0.0f) {
            return null;
        }
        return near >= 0.0f ? near : far;
    }

    private ClientDisplays() {}
}
