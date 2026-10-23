package tr.agasis.aebd.common;

import com.mojang.math.Transformation;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A display's placement expressed the way a builder thinks about it: a world-space centre, an
 * orientation and a size.
 *
 * <p>Vanilla stores a display as an entity position plus a {@link Transformation} whose translation
 * is applied before rotation and scale, so rotating or resizing a raw display also swings it through
 * space. This record hides that: the centre is pinned to the kind's {@linkplain DisplayKind#pivot()
 * pivot}, and the translation is recomputed on the way out so the pivot always lands exactly on the
 * stored centre.
 */
public record DisplayXform(Vec3 center, Quaternionf left, Vector3f scale, Quaternionf right) {

    public static final StreamCodec<ByteBuf, DisplayXform> STREAM_CODEC = StreamCodec.of(
            (buf, xform) -> {
                buf.writeDouble(xform.center.x);
                buf.writeDouble(xform.center.y);
                buf.writeDouble(xform.center.z);
                FriendlyByteBuf.writeQuaternion(buf, xform.left);
                FriendlyByteBuf.writeVector3f(buf, xform.scale);
                FriendlyByteBuf.writeQuaternion(buf, xform.right);
            },
            buf -> new DisplayXform(
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    FriendlyByteBuf.readQuaternion(buf),
                    FriendlyByteBuf.readVector3f(buf),
                    FriendlyByteBuf.readQuaternion(buf)));

    public static DisplayXform atCenter(Vec3 center) {
        return new DisplayXform(center, new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf());
    }

    /** Recovers the builder-facing form from a live display entity's raw vanilla state. */
    public static DisplayXform from(Vec3 entityPos, Transformation transformation, DisplayKind kind) {
        Vector3f offset = transformation.getMatrix().transformPosition(kind.pivot());
        return new DisplayXform(
                entityPos.add(offset.x, offset.y, offset.z),
                new Quaternionf(transformation.getLeftRotation()),
                new Vector3f(transformation.getScale()),
                new Quaternionf(transformation.getRightRotation()));
    }

    /**
     * The vanilla transformation to store on the entity, assuming the entity sits exactly at
     * {@link #center()}. The translation cancels out the pivot so rotation and scale happen in place.
     */
    public Transformation toTransformation(DisplayKind kind) {
        Transformation unshifted = new Transformation(new Vector3f(), this.left, this.scale, this.right);
        Vector3f offset = unshifted.getMatrix().transformPosition(kind.pivot());
        return new Transformation(offset.negate(), this.left, this.scale, this.right);
    }

    /** Local-space corner to world-space matrix, used for picking and outline drawing. */
    public org.joml.Matrix4f worldMatrix(DisplayKind kind) {
        org.joml.Matrix4f matrix = new org.joml.Matrix4f().translate(
                (float) this.center.x, (float) this.center.y, (float) this.center.z);
        return matrix.mul(this.toTransformation(kind).getMatrix());
    }

    public DisplayXform withCenter(Vec3 newCenter) {
        return new DisplayXform(newCenter, new Quaternionf(this.left), new Vector3f(this.scale), new Quaternionf(this.right));
    }

    public DisplayXform translated(Vec3 delta) {
        return withCenter(this.center.add(delta));
    }

    public DisplayXform withScale(Vector3f newScale) {
        return new DisplayXform(this.center, new Quaternionf(this.left), new Vector3f(newScale), new Quaternionf(this.right));
    }

    public DisplayXform withLeft(Quaternionf newLeft) {
        return new DisplayXform(this.center, new Quaternionf(newLeft), new Vector3f(this.scale), new Quaternionf(this.right));
    }

    /** Rotates the display, and its centre, about an arbitrary world-space point. */
    public DisplayXform rotatedAround(Vec3 pivotPoint, Quaternionf worldRotation) {
        Vector3f relative = new Vector3f(
                (float) (this.center.x - pivotPoint.x),
                (float) (this.center.y - pivotPoint.y),
                (float) (this.center.z - pivotPoint.z));
        worldRotation.transform(relative);
        return new DisplayXform(
                pivotPoint.add(relative.x, relative.y, relative.z),
                new Quaternionf(worldRotation).mul(this.left),
                new Vector3f(this.scale),
                new Quaternionf(this.right));
    }

    /** Mirrors the display, and its centre, across a world-space plane normal to {@code axis}. */
    public DisplayXform mirroredAround(Vec3 pivotPoint, int axis) {
        double cx = this.center.x;
        double cy = this.center.y;
        double cz = this.center.z;
        Vector3f flipped = new Vector3f(this.scale);
        switch (axis) {
            case 0 -> {
                cx = 2.0 * pivotPoint.x - cx;
                flipped.x = -flipped.x;
            }
            case 1 -> {
                cy = 2.0 * pivotPoint.y - cy;
                flipped.y = -flipped.y;
            }
            default -> {
                cz = 2.0 * pivotPoint.z - cz;
                flipped.z = -flipped.z;
            }
        }
        return new DisplayXform(new Vec3(cx, cy, cz), new Quaternionf(this.left), flipped, new Quaternionf(this.right));
    }

    public boolean closeTo(DisplayXform other) {
        return this.center.distanceToSqr(other.center) < 1.0E-10
                && this.left.equals(other.left, 1.0E-6f)
                && this.scale.equals(other.scale, 1.0E-6f)
                && this.right.equals(other.right, 1.0E-6f);
    }
}
