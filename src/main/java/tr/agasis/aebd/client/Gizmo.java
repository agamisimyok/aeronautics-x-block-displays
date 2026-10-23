package tr.agasis.aebd.client;

import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry for the move/rotate/scale manipulator: where its handles sit, which one the
 * crosshair ray is pointing at, and the scalar read-outs the drag math turns into transforms.
 *
 * <p>The gizmo is world-axis aligned (global orientation, like Blender's default). It holds no
 * state; {@link EditorSession} owns the active drag and feeds these functions the live ray.
 *
 * <p>Handle ids: {@code 0,1,2} = X/Y/Z axis; {@code 3,4,5} = the plane whose normal is X/Y/Z;
 * {@code 6} = uniform centre. {@link #NONE} is "nothing".
 */
public final class Gizmo {

    public static final int NONE = -1;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;
    public static final int PLANE_X = 3;
    public static final int PLANE_Y = 4;
    public static final int PLANE_Z = 5;
    public static final int UNIFORM = 6;

    /** The gizmo is drawn a constant size on screen, so its world size tracks camera distance. */
    private static final double SIZE_FACTOR = 0.16;
    private static final double MIN_SIZE = 0.6;

    private static final double AXIS_PICK = 0.38;
    private static final double RING_PICK = 0.26;
    private static final double BOX_PICK = 0.30;
    private static final double PLANE_LO = 0.22;
    private static final double PLANE_HI = 0.5;

    public static boolean isAxis(int handle) {
        return handle >= AXIS_X && handle <= AXIS_Z;
    }

    public static boolean isPlane(int handle) {
        return handle >= PLANE_X && handle <= PLANE_Z;
    }

    /** The axis a handle acts on: the axis itself, or a plane's normal. */
    public static int axisOf(int handle) {
        if (isAxis(handle)) {
            return handle;
        }
        if (isPlane(handle)) {
            return handle - PLANE_X;
        }
        return -1;
    }

    public static Vec3 axisVec(int axis) {
        return switch (axis) {
            case AXIS_X -> new Vec3(1.0, 0.0, 0.0);
            case AXIS_Y -> new Vec3(0.0, 1.0, 0.0);
            default -> new Vec3(0.0, 0.0, 1.0);
        };
    }

    public static double size(Vec3 center, Vec3 camera) {
        return Math.max(MIN_SIZE, camera.distanceTo(center) * SIZE_FACTOR);
    }

    public static Vec3 axisEnd(Vec3 center, double size, int axis) {
        return center.add(axisVec(axis).scale(size));
    }

    // ------------------------------------------------------------------ picking

    public static int pick(GizmoMode mode, Vec3 center, double size, Vec3 eye, Vec3 look, Vec3[] axes) {
        return switch (mode) {
            case MOVE -> pickMove(center, size, eye, look, axes);
            case ROTATE -> pickRotate(center, size, eye, look, axes);
            case SCALE -> pickScale(center, size, eye, look, axes);
            case NONE -> NONE;
        };
    }

    private static int pickMove(Vec3 center, double size, Vec3 eye, Vec3 look, Vec3[] axes) {
        // Move uses unambiguous arrow handles. Plane constraints remain available through Shift+X/Y/Z.
        return nearestAxis(center, size, eye, look, AXIS_PICK, axes);
    }

    private static int pickScale(Vec3 center, double size, Vec3 eye, Vec3 look, Vec3[] axes) {
        // The centre cube gets a generous, explicit uniform-scale target.
        if (rayPointDistance(eye, look, center) < BOX_PICK * size * 0.72) {
            return UNIFORM;
        }
        // Axis scale handles are selectable along their entire stem as well as their end cubes.
        return nearestAxis(center, size, eye, look, AXIS_PICK, axes);
    }

    private static int pickRotate(Vec3 center, double size, Vec3 eye, Vec3 look, Vec3[] axes) {
        int best = NONE;
        double bestDistance = RING_PICK * size;
        for (int axis = 0; axis < 3; axis++) {
            Vec3 u = axes[(axis + 1) % 3];
            Vec3 v = axes[(axis + 2) % 3];
            Vec3 previous = center.add(u.scale(size));
            for (int segment = 1; segment <= 48; segment++) {
                double angle = Math.PI * 2.0 * segment / 48.0;
                Vec3 current = center.add(u.scale(Math.cos(angle) * size))
                        .add(v.scale(Math.sin(angle) * size));
                double distance = raySegmentDistance(eye, look, previous, current);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = axis;
                }
                previous = current;
            }
        }
        return best;
    }

    private static int nearestAxis(Vec3 center, double size, Vec3 eye, Vec3 look, double threshold, Vec3[] axes) {
        int best = NONE;
        double bestDistance = threshold * size;
        for (int axis = 0; axis < 3; axis++) {
            // Include a short area behind the pivot too. From oblique views the rendered arrow's
            // wide base visually spills across the pivot even though its mathematical stem does not.
            Vec3 direction = axes[axis];
            Vec3 start = center.subtract(direction.scale(size * 0.28));
            Vec3 end = axisEnd(center, size, axis).add(direction.scale(size * 0.08));
            double distance = raySegmentDistance(eye, look, start, end);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = axis;
            }
        }
        return best;
    }

    private static boolean insidePlaneHandle(Vec3 center, double size, int normal, Vec3 eye, Vec3 look) {
        Vec3 hit = planeIntersect(center, axisVec(normal), eye, look);
        if (hit == null) {
            return false;
        }
        int u = (normal + 1) % 3;
        int v = (normal + 2) % 3;
        double a = hit.subtract(center).dot(axisVec(u));
        double b = hit.subtract(center).dot(axisVec(v));
        return a >= PLANE_LO * size && a <= PLANE_HI * size
                && b >= PLANE_LO * size && b <= PLANE_HI * size;
    }

    // ------------------------------------------------------------------ drag read-outs

    /** Signed distance of the crosshair ray's nearest point from {@code center} along {@code axis}. */
    public static double axisParam(Vec3 center, int axis, Vec3 eye, Vec3 look) {
        Vec3 dir = axisVec(axis);
        Vec3 w = center.subtract(eye);
        double b = dir.dot(look);
        double denominator = 1.0 - b * b;
        if (Math.abs(denominator) < 1.0E-6) {
            return 0.0;
        }
        return -(b * w.dot(look) - w.dot(dir)) / denominator;
    }

    /**
     * A camera-facing plane that contains the requested axis. Intersecting this plane is much more
     * stable than solving the nearest points of two almost-parallel infinite lines.
     */
    public static Vec3 axisDragPlaneNormal(int axis, Vec3 look) {
        Vec3 axisVector = axisVec(axis);
        Vec3 projectedLook = look.subtract(axisVector.scale(look.dot(axisVector)));
        if (projectedLook.lengthSqr() > 1.0E-4) {
            return projectedLook.normalize();
        }
        Vec3 fallback = axis == AXIS_Y ? axisVec(AXIS_X) : axisVec(AXIS_Y);
        return fallback.subtract(axisVector.scale(fallback.dot(axisVector))).normalize();
    }

    public static double axisParamOnPlane(Vec3 center, int axis, Vec3 planeNormal, Vec3 eye, Vec3 look,
            double fallback) {
        Vec3 hit = planeIntersect(center, planeNormal, eye, look);
        return hit == null ? fallback : hit.subtract(center).dot(axisVec(axis));
    }

    /** Angle of the crosshair ray within the plane normal to {@code axis}, measured about {@code center}. */
    public static double rotationAngle(Vec3 center, int axis, Vec3 eye, Vec3 look) {
        Vec3 hit = planeIntersect(center, axisVec(axis), eye, look);
        if (hit == null) {
            return 0.0;
        }
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        Vec3 rel = hit.subtract(center);
        return Math.atan2(rel.dot(axisVec(v)), rel.dot(axisVec(u)));
    }

    /** Perpendicular distance from {@code center} to the ray, used as the uniform-scale read-out. */
    public static double radialParam(Vec3 center, Vec3 eye, Vec3 look) {
        Vec3 w = center.subtract(eye);
        double along = w.dot(look);
        Vec3 closest = eye.add(look.scale(along));
        return Math.max(1.0E-4, closest.distanceTo(center));
    }

    @Nullable
    public static Vec3 planeIntersect(Vec3 anchor, Vec3 normal, Vec3 eye, Vec3 look) {
        double denominator = normal.dot(look);
        if (Math.abs(denominator) < 1.0E-6) {
            return null;
        }
        double t = anchor.subtract(eye).dot(normal) / denominator;
        if (t < 0.0) {
            return null;
        }
        return eye.add(look.scale(t));
    }

    // ------------------------------------------------------------------ ray helpers

    private static double rayPointDistance(Vec3 eye, Vec3 look, Vec3 point) {
        double along = Math.max(0.0, point.subtract(eye).dot(look));
        return eye.add(look.scale(along)).distanceTo(point);
    }

    private static double raySegmentDistance(Vec3 eye, Vec3 look, Vec3 a, Vec3 b) {
        Vec3 segment = b.subtract(a);
        if (segment.lengthSqr() < 1.0E-12) {
            return rayPointDistance(eye, look, a);
        }
        // Distance-to-a-ray along a finite segment is convex. Ternary minimisation avoids the
        // near-parallel and endpoint failures of the previous closed-form implementation.
        double low = 0.0;
        double high = 1.0;
        for (int i = 0; i < 24; i++) {
            double third = (high - low) / 3.0;
            double u1 = low + third;
            double u2 = high - third;
            double d1 = rayPointDistance(eye, look, a.add(segment.scale(u1)));
            double d2 = rayPointDistance(eye, look, a.add(segment.scale(u2)));
            if (d1 <= d2) {
                high = u2;
            } else {
                low = u1;
            }
        }
        return rayPointDistance(eye, look, a.add(segment.scale((low + high) * 0.5)));
    }

    private Gizmo() {}
}
