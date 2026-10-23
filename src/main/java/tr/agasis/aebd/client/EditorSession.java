package tr.agasis.aebd.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tr.agasis.aebd.common.DisplayData;
import tr.agasis.aebd.common.DisplayKind;
import tr.agasis.aebd.common.DisplayProps;
import tr.agasis.aebd.common.DisplayXform;
import tr.agasis.aebd.item.DisplayEditorItem;
import tr.agasis.aebd.net.AEBDPayloads;

/**
 * All editor state and behaviour, client side.
 *
 * <p>The server owns display entities, so every edit is a packet and the result arrives a tick or
 * two later. To keep the tool feeling direct, intended transforms are also kept in a
 * {@linkplain #previewOf(int) preview} map: outlines draw from it immediately, follow-up edits
 * compose on top of it, and entries retire once the real entity catches up.
 */
public final class EditorSession {

    public static final EditorSession INSTANCE = new EditorSession();

    public enum Axis {
        FREE, X, Y, Z;

        public Vec3 vector() {
            return switch (this) {
                case X -> new Vec3(1.0, 0.0, 0.0);
                case Y -> new Vec3(0.0, 1.0, 0.0);
                case Z -> new Vec3(0.0, 0.0, 1.0);
                case FREE -> Vec3.ZERO;
            };
        }
    }

    /**
     * Precision levels, stepped together so one indicator covers move, rotate and scale. The finest
     * entries go well below one pixel of a block so displays can be positioned as precisely as Axiom
     * allows; all three arrays share a length so one index drives them together.
     */
    private static final double[] MOVE_STEPS = {
            1.0 / 1024.0, 1.0 / 256.0, 1.0 / 128.0, 1.0 / 64.0, 1.0 / 32.0,
            1.0 / 16.0, 1.0 / 8.0, 1.0 / 4.0, 1.0 / 2.0, 1.0};
    private static final double[] ROTATE_STEPS = {
            0.05, 0.25, 0.5, 1.0, 2.5, 5.0, 15.0, 22.5, 45.0, 90.0};
    private static final double[] SCALE_STEPS = {
            1.0 / 1024.0, 1.0 / 256.0, 1.0 / 128.0, 1.0 / 64.0, 1.0 / 32.0,
            1.0 / 16.0, 1.0 / 8.0, 1.0 / 4.0, 1.0 / 2.0, 1.0};
    private static final int DEFAULT_PRECISION = 5;

    public static final double PICK_RANGE = 96.0;
    private static final double SELECT_ALL_RADIUS = 48.0;
    private static final int CHUNK = 128;
    private static final int HISTORY_LIMIT = 64;
    private static final int PENDING_LIMIT = 64;
    private static final float MIN_SCALE = 1.0f / 64.0f;

    private final Set<Integer> selection = new LinkedHashSet<>();
    private final Map<Integer, DisplayXform> preview = new HashMap<>();
    private final List<DisplayData> clipboard = new ArrayList<>();
    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();
    private final Map<Integer, Integer> idRemap = new HashMap<>();
    private final Map<Integer, Consumer<List<Integer>>> pending = new HashMap<>();

    private int nextRequestId = 1;
    private Axis axis = Axis.FREE;
    /** When true and {@link #axis} is not FREE, the constraint is the plane whose normal is that axis. */
    private boolean plane = false;
    private int precision = DEFAULT_PRECISION;
    private boolean snapping = true;
    private boolean localAxes = false;
    private int hovered = -1;
    private GizmoMode gizmoMode = GizmoMode.NONE;
    private int hoveredHandle = Gizmo.NONE;
    @Nullable
    private Grab grab;
    @Nullable
    private GizmoDrag gizmoDrag;

    private EditorSession() {}

    // ---------------------------------------------------------------- state

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null
                && DisplayEditorItem.isEditing(mc.player.getMainHandItem());
    }

    public Set<Integer> selection() {
        return this.selection;
    }

    public int hovered() {
        return this.hovered;
    }

    public Axis axis() {
        return this.axis;
    }

    public boolean plane() {
        return this.plane;
    }

    public boolean snapping() {
        return this.snapping;
    }

    public boolean localAxes() {
        return this.localAxes;
    }

    public void toggleLocalAxes() {
        this.localAxes = !this.localAxes;
        this.hoveredHandle = Gizmo.NONE;
        feedback(Component.translatable(this.localAxes ? "aebd.message.axes_local" : "aebd.message.axes_world"));
    }

    public Vec3 gizmoAxisVector(int axis) {
        Vec3 world = Gizmo.axisVec(axis);
        if (!this.localAxes) {
            return world;
        }
        Map<Integer, DisplayXform> xforms = currentXforms();
        if (xforms.isEmpty()) {
            return world;
        }
        DisplayXform xform = xforms.values().iterator().next();
        Vector3f vector = new Vector3f((float) world.x, (float) world.y, (float) world.z);
        new Quaternionf(xform.left()).mul(xform.right()).transform(vector);
        return new Vec3(vector.x, vector.y, vector.z).normalize();
    }

    public Vec3[] gizmoAxes() {
        return new Vec3[] {gizmoAxisVector(0), gizmoAxisVector(1), gizmoAxisVector(2)};
    }

    public boolean grabbing() {
        return this.grab != null;
    }

    public GizmoMode gizmoMode() {
        return this.gizmoMode;
    }

    public int hoveredHandle() {
        return this.hoveredHandle;
    }

    public boolean draggingGizmo() {
        return this.gizmoDrag != null;
    }

    public void setGizmoMode(GizmoMode mode) {
        this.gizmoMode = this.gizmoMode == mode ? GizmoMode.NONE : mode;
        this.hoveredHandle = Gizmo.NONE;
    }

    public int clipboardSize() {
        return this.clipboard.size();
    }

    public double moveStep() {
        return MOVE_STEPS[this.precision];
    }

    public double rotateStep() {
        return ROTATE_STEPS[this.precision];
    }

    public double scaleStep() {
        return SCALE_STEPS[this.precision];
    }

    @Nullable
    public DisplayXform previewOf(int entityId) {
        return this.preview.get(entityId);
    }

    public void reset() {
        this.selection.clear();
        this.preview.clear();
        this.clipboard.clear();
        this.undoStack.clear();
        this.redoStack.clear();
        this.idRemap.clear();
        this.pending.clear();
        this.grab = null;
        this.gizmoDrag = null;
        this.hovered = -1;
        this.hoveredHandle = Gizmo.NONE;
    }

    // ---------------------------------------------------------------- ticking

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.level == null || mc.player == null) {
            if (this.grab != null) {
                cancelGrab();
            }
            if (this.gizmoDrag != null) {
                cancelGizmoDrag();
            }
            this.hovered = -1;
            this.hoveredHandle = Gizmo.NONE;
            return;
        }
        prune(mc.level);

        if (this.gizmoDrag != null) {
            // MouseButton.Pre ends the drag. While active the OS cursor drives the transform and
            // the camera remains locked, independently of the player's view ray.
            updateGizmoDrag(mc);
            this.hovered = -1;
            return;
        }

        this.hovered = this.grab != null ? -1 : ClientDisplays.pick(
                mc.level, mc.player.getEyePosition(), mc.player.getViewVector(1.0f), PICK_RANGE);

        if (this.grab != null) {
            updateGrab(mc);
        } else {
            updateHandleHover(mc);
        }
    }

    private void updateHandleHover(Minecraft mc) {
        if (this.gizmoMode == GizmoMode.NONE || this.selection.isEmpty()) {
            this.hoveredHandle = Gizmo.NONE;
            return;
        }
        Vec3 center = gizmoCenter();
        double size = gizmoSize(mc.getEntityRenderDispatcher().camera.getPosition());
        this.hoveredHandle = Gizmo.pick(this.gizmoMode, center, size,
                mc.player.getEyePosition(), mc.player.getViewVector(1.0f), gizmoAxes());
    }

    public Vec3 gizmoCenter() {
        return centerOfXforms(currentXforms().values());
    }

    /**
     * Gizmo size blends a constant-on-screen term with the selection's own radius, so the handles
     * always reach past the display instead of hiding inside a large or scaled one.
     */
    public double gizmoSize(Vec3 camera) {
        Vec3 center = gizmoCenter();
        return Math.max(Gizmo.size(center, camera), selectionRadius(center) + 0.4);
    }

    private double selectionRadius(Vec3 center) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return 0.0;
        }
        double maxSq = 0.0;
        for (int id : this.selection) {
            Display display = ClientDisplays.displayById(level, id);
            DisplayKind kind = DisplayKind.of(display);
            if (kind == null) {
                continue;
            }
            DisplayXform xform = this.preview.getOrDefault(id, ClientDisplays.xformOf(display));
            if (xform == null) {
                continue;
            }
            for (Vector3f corner : ClientDisplays.corners(xform, kind)) {
                maxSq = Math.max(maxSq, center.distanceToSqr(corner.x, corner.y, corner.z));
            }
        }
        return Math.sqrt(maxSq);
    }

    private void prune(Level level) {
        this.selection.removeIf(id -> ClientDisplays.displayById(level, id) == null);
        this.preview.entrySet().removeIf(entry -> {
            Display display = ClientDisplays.displayById(level, entry.getKey());
            if (display == null) {
                return true;
            }
            DisplayXform actual = ClientDisplays.xformOf(display);
            return actual != null && actual.closeTo(entry.getValue());
        });
    }

    // ---------------------------------------------------------------- selection

    public void setSelection(Collection<Integer> ids) {
        this.selection.clear();
        this.selection.addAll(ids);
    }

    public void clearSelection() {
        this.selection.clear();
    }

    public void clickSelect(boolean additive) {
        if (this.hovered < 0) {
            if (!additive) {
                this.selection.clear();
            }
            return;
        }
        if (additive) {
            if (!this.selection.remove(this.hovered)) {
                this.selection.add(this.hovered);
            }
        } else {
            this.selection.clear();
            this.selection.add(this.hovered);
        }
    }

    public void selectAllNearby() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        this.selection.clear();
        for (Display display : ClientDisplays.nearby(mc.level, mc.player.position(), SELECT_ALL_RADIUS)) {
            this.selection.add(display.getId());
        }
        feedback(Component.translatable("aebd.message.selected", this.selection.size()));
    }

    // ---------------------------------------------------------------- modifiers

    /**
     * Cycles the active constraint. Without plane mode this is a single-axis lock; with it (Shift held,
     * Blender style) the motion is freed on the two axes perpendicular to {@code requested} instead.
     * Pressing the same combination again returns to the unconstrained state.
     */
    public void toggleAxis(Axis requested, boolean planeMode) {
        if (this.axis == requested && this.plane == planeMode) {
            this.axis = Axis.FREE;
            this.plane = false;
        } else {
            this.axis = requested;
            this.plane = planeMode;
        }
    }

    public void stepPrecision(int delta) {
        this.precision = Mth.clamp(this.precision + delta, 0, MOVE_STEPS.length - 1);
    }

    public void toggleSnapping() {
        this.snapping = !this.snapping;
    }

    // ---------------------------------------------------------------- grab

    private static final class Grab {
        private final Map<Integer, DisplayXform> originals;
        private final Vec3 anchor;
        private double distance;
        private Vec3 delta = Vec3.ZERO;

        private Grab(Map<Integer, DisplayXform> originals, Vec3 anchor, double distance) {
            this.originals = originals;
            this.anchor = anchor;
            this.distance = distance;
        }
    }

    public void toggleGrab() {
        if (this.grab != null) {
            confirmGrab();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Map<Integer, DisplayXform> originals = currentXforms();
        if (originals.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        Vec3 anchor = centerOfXforms(originals.values());
        double distance = Mth.clamp(mc.player.getEyePosition().distanceTo(anchor), 1.0, 64.0);
        this.grab = new Grab(originals, anchor, distance);
    }

    public void confirmGrab() {
        Grab current = this.grab;
        this.grab = null;
        if (current == null || current.delta.equals(Vec3.ZERO)) {
            return;
        }
        Map<Integer, DisplayXform> after = new LinkedHashMap<>();
        current.originals.forEach((id, xform) -> after.put(id, xform.translated(current.delta)));
        pushUndo(new UndoEntry.Transform(current.originals, after));
    }

    public void cancelGrab() {
        Grab current = this.grab;
        this.grab = null;
        if (current != null && !current.delta.equals(Vec3.ZERO)) {
            sendXforms(current.originals);
        }
    }

    public void adjustGrabDistance(double amount) {
        if (this.grab != null) {
            this.grab.distance = Mth.clamp(this.grab.distance + amount, 0.5, 64.0);
        }
    }

    private void updateGrab(Minecraft mc) {
        Grab current = this.grab;
        if (current == null || mc.player == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 target;
        if (this.axis == Axis.FREE) {
            target = eye.add(look.scale(current.distance));
        } else if (this.plane) {
            Vec3 hit = Gizmo.planeIntersect(current.anchor, this.axis.vector(), eye, look);
            target = hit == null ? current.anchor.add(current.delta) : hit;
        } else {
            target = closestPointOnAxis(current.anchor, this.axis.vector(), eye, look);
        }
        Vec3 delta = snapDelta(target.subtract(current.anchor));
        if (delta.equals(current.delta)) {
            return;
        }
        current.delta = delta;
        Map<Integer, DisplayXform> moved = new LinkedHashMap<>();
        current.originals.forEach((id, xform) -> moved.put(id, xform.translated(delta)));
        sendXforms(moved);
    }

    /**
     * Where the crosshair ray comes closest to the constraint line, so an axis-locked grab tracks
     * the cursor along that one axis instead of following it into the screen.
     */
    private static Vec3 closestPointOnAxis(Vec3 anchor, Vec3 axisDir, Vec3 eye, Vec3 look) {
        Vec3 w = anchor.subtract(eye);
        double b = axisDir.dot(look);
        double denominator = 1.0 - b * b;
        if (Math.abs(denominator) < 1.0E-6) {
            return anchor;
        }
        double t = (b * w.dot(look) - w.dot(axisDir)) / denominator;
        return anchor.add(axisDir.scale(Mth.clamp(t, -256.0, 256.0)));
    }

    private Vec3 snapDelta(Vec3 raw) {
        boolean blockStep = net.minecraft.client.gui.screens.Screen.hasAltDown();
        if (!this.snapping && !blockStep) {
            return raw;
        }
        double step = blockStep ? 1.0 : moveStep();
        return new Vec3(
                Math.round(raw.x / step) * step,
                Math.round(raw.y / step) * step,
                Math.round(raw.z / step) * step);
    }

    // ---------------------------------------------------------------- gizmo drag

    private static final class GizmoDrag {
        private final int handle;
        private final Map<Integer, DisplayXform> originals;
        private final Vec3 center;
        private final double size;
        private final boolean shift;
        private double startMouseX;
        private double startMouseY;
        private int cursorSettleTicks;
        private final double screenX;
        private final double screenY;
        private Map<Integer, DisplayXform> last;

        private GizmoDrag(int handle, Map<Integer, DisplayXform> originals, Vec3 center, double size,
                boolean shift, double startMouseX, double startMouseY, double screenX, double screenY) {
            this.handle = handle;
            this.originals = originals;
            this.center = center;
            this.size = size;
            this.shift = shift;
            this.startMouseX = startMouseX;
            this.startMouseY = startMouseY;
            this.cursorSettleTicks = 2;
            this.screenX = screenX;
            this.screenY = screenY;
            this.last = originals;
        }
    }

    /** Begins dragging the handle currently under the crosshair. Called on the attack button press. */
    public void beginGizmoDrag(boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || this.gizmoMode == GizmoMode.NONE || this.hoveredHandle == Gizmo.NONE) {
            return;
        }
        Map<Integer, DisplayXform> originals = currentXforms();
        if (originals.isEmpty()) {
            return;
        }
        Vec3 center = centerOfXforms(originals.values());
        double size = gizmoSize(mc.getEntityRenderDispatcher().camera.getPosition());
        int handle = this.hoveredHandle;
        int axis = Gizmo.axisOf(handle);

        double screenX = 1.0;
        double screenY = 0.0;
        if (handle != Gizmo.UNIFORM) {
            Vec3 axisVector = gizmoAxisVector(axis);
            org.joml.Vector3f left = mc.getEntityRenderDispatcher().camera.getLeftVector();
            org.joml.Vector3f up = mc.getEntityRenderDispatcher().camera.getUpVector();
            // GLFW Y grows downwards, hence the minus on the camera-up component.
            screenX = -axisVector.dot(new Vec3(left.x, left.y, left.z));
            screenY = -axisVector.dot(new Vec3(up.x, up.y, up.z));
            double length = Math.sqrt(screenX * screenX + screenY * screenY);
            if (length > 0.08) {
                screenX /= length;
                screenY /= length;
            } else {
                // An axis pointing almost straight at the camera has no useful projection.
                // Horizontal mouse motion is a stable, predictable fallback.
                screenX = 1.0;
                screenY = 0.0;
            }
            if (this.gizmoMode == GizmoMode.ROTATE) {
                double oldX = screenX;
                screenX = -screenY;
                screenY = oldX;
            }
        }
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        this.gizmoDrag = new GizmoDrag(handle, originals, center, size, shift,
                mouseX, mouseY, screenX, screenY);
        // Releasing the GLFW cursor makes it visible and stops MouseHandler from turning the camera.
        // Keyboard input remains active, so the player can still move with WASD during the drag.
        mc.mouseHandler.releaseMouse();
    }

    private void updateGizmoDrag(Minecraft mc) {
        GizmoDrag drag = this.gizmoDrag;
        if (drag == null || mc.player == null) {
            return;
        }
        int handle = drag.handle;
        int axis = Gizmo.axisOf(handle);
        Map<Integer, DisplayXform> result = new LinkedHashMap<>();
        if (drag.cursorSettleTicks-- > 0) {
            // releaseMouse may deliver the OS cursor-centering callback one frame later. Treat its
            // final position as zero so grabbing away from the pivot never teleports the display.
            drag.startMouseX = mc.mouseHandler.xpos();
            drag.startMouseY = mc.mouseHandler.ypos();
            return;
        }
        double dx = mc.mouseHandler.xpos() - drag.startMouseX;
        double dy = mc.mouseHandler.ypos() - drag.startMouseY;
        double pixels = dx * drag.screenX + dy * drag.screenY;
        // The gizmo is rendered at an approximately constant screen size. One hundred cursor
        // pixels therefore corresponds to one rendered handle length at every camera distance.
        double handleUnits = pixels / 100.0;

        switch (this.gizmoMode) {
            case MOVE -> {
                double raw = handleUnits * drag.size;
                boolean relativeBlockStep = net.minecraft.client.gui.screens.Screen.hasAltDown()
                        && moveStep() < 1.0 - 1.0E-9;
                double step = relativeBlockStep ? 1.0 : moveStep();
                // Alt snaps the delta, never the world coordinate. This deliberately preserves
                // the starting fraction: 0.4 + one Alt step becomes 1.4, not 1.0 or 2.0.
                double amount = (this.snapping || relativeBlockStep)
                        ? Math.round(raw / step) * step : raw;
                Vec3 delta = gizmoAxisVector(axis).scale(amount);
                drag.originals.forEach((id, xform) -> result.put(id, xform.translated(delta)));
            }
            case ROTATE -> {
                double raw = handleUnits;
                if (this.snapping) {
                    double stepRad = Math.toRadians(rotateStep());
                    raw = Math.round(raw / stepRad) * stepRad;
                }
                Vec3 axisVec = gizmoAxisVector(axis);
                Quaternionf rotation = new Quaternionf().rotateAxis(
                        (float) raw, (float) axisVec.x, (float) axisVec.y, (float) axisVec.z);
                drag.originals.forEach((id, xform) -> result.put(id, xform.rotatedAround(drag.center, rotation)));
            }
            case SCALE -> {
                if (handle == Gizmo.UNIFORM) {
                    double factor = 1.0 + (dx - dy) / 150.0;
                    applyScale(drag, result, new Vector3f((float) factor, (float) factor, (float) factor), drag.center);
                } else {
                    double factor = 1.0 + handleUnits;
                    Vector3f factorVec = new Vector3f(1.0f, 1.0f, 1.0f);
                    factorVec.setComponent(axis, (float) factor);
                    // Shift anchors the opposite face so the shape stretches toward the dragged side only.
                    Vec3 pivot = drag.shift ? drag.center.subtract(gizmoAxisVector(axis).scale(drag.size)) : drag.center;
                    applyScale(drag, result, factorVec, pivot);
                }
            }
            case NONE -> {
                return;
            }
        }
        drag.last = result;
        sendXforms(result);
    }

    private void applyScale(GizmoDrag drag, Map<Integer, DisplayXform> result, Vector3f factor, Vec3 pivot) {
        Vector3f clamped = new Vector3f(
                clampFactor(factor.x), clampFactor(factor.y), clampFactor(factor.z));
        drag.originals.forEach((id, xform) -> {
            Vec3 c = xform.center();
            Vec3 newCenter = new Vec3(
                    pivot.x + (c.x - pivot.x) * clamped.x,
                    pivot.y + (c.y - pivot.y) * clamped.y,
                    pivot.z + (c.z - pivot.z) * clamped.z);
            Vector3f scale = new Vector3f(xform.scale());
            scale.mul(clamped);
            boolean blockStep = net.minecraft.client.gui.screens.Screen.hasAltDown();
            if (this.snapping || blockStep) {
                double step = blockStep ? 1.0 : scaleStep();
                scale.set(
                        snapScale(scale.x, step),
                        snapScale(scale.y, step),
                        snapScale(scale.z, step));
            }
            result.put(id, new DisplayXform(newCenter, new Quaternionf(xform.left()), scale, new Quaternionf(xform.right())));
        });
    }

    public void confirmGizmoDrag() {
        GizmoDrag drag = this.gizmoDrag;
        this.gizmoDrag = null;
        restoreMouseCapture();
        if (drag == null) {
            return;
        }
        boolean changed = drag.last.entrySet().stream()
                .anyMatch(entry -> !entry.getValue().closeTo(drag.originals.get(entry.getKey())));
        if (changed) {
            pushUndo(new UndoEntry.Transform(drag.originals, drag.last));
        }
    }

    public void cancelGizmoDrag() {
        GizmoDrag drag = this.gizmoDrag;
        this.gizmoDrag = null;
        restoreMouseCapture();
        if (drag != null) {
            sendXforms(drag.originals);
        }
    }

    private static void restoreMouseCapture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.grabMouse();
        }
    }

    /** Snaps the whole selection so its centre lands on the block face (or point) under the crosshair. */
    public void placeAtLook() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Map<Integer, DisplayXform> before = currentXforms();
        if (before.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        Vec3 target = placementCenter(mc);
        Vec3 delta = snapDelta(target.subtract(centerOfXforms(before.values())));
        Map<Integer, DisplayXform> after = new LinkedHashMap<>();
        before.forEach((id, xform) -> after.put(id, xform.translated(delta)));
        applyAndRecord(before, after);
    }

    private static double wrapAngle(double radians) {
        while (radians > Math.PI) {
            radians -= Math.PI * 2.0;
        }
        while (radians < -Math.PI) {
            radians += Math.PI * 2.0;
        }
        return radians;
    }

    private static float clampFactor(float factor) {
        float magnitude = Mth.clamp(Math.abs(factor), 0.02f, 50.0f);
        return factor < 0.0f ? -magnitude : magnitude;
    }

    private static float snapScale(float value, double step) {
        float snapped = (float) (Math.round(value / step) * step);
        return Math.abs(snapped) < MIN_SCALE ? (value < 0 ? -MIN_SCALE : MIN_SCALE) : snapped;
    }

    // ---------------------------------------------------------------- transforms

    public void nudge(int sign) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 direction;
        if (this.axis == Axis.FREE) {
            Vec3 look = mc.player.getViewVector(1.0f);
            Direction nearest = Direction.getNearest(look.x, look.y, look.z);
            direction = new Vec3(nearest.getStepX(), nearest.getStepY(), nearest.getStepZ());
        } else {
            direction = this.axis.vector();
        }
        double step = net.minecraft.client.gui.screens.Screen.hasAltDown() ? 1.0 : moveStep();
        Vec3 delta = direction.scale(sign * step);
        transformSelection(xform -> xform.translated(delta));
    }

    public void rotate(int sign) {
        Vec3 axisVector = this.axis == Axis.FREE ? new Vec3(0.0, 1.0, 0.0) : this.axis.vector();
        float angle = (float) Math.toRadians(sign * rotateStep());
        Quaternionf rotation = new Quaternionf().rotateAxis(
                angle, (float) axisVector.x, (float) axisVector.y, (float) axisVector.z);
        Map<Integer, DisplayXform> before = currentXforms();
        if (before.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        Vec3 pivot = centerOfXforms(before.values());
        Map<Integer, DisplayXform> after = new LinkedHashMap<>();
        before.forEach((id, xform) -> after.put(id, xform.rotatedAround(pivot, rotation)));
        applyAndRecord(before, after);
    }

    public void scale(int sign) {
        double delta = sign * (net.minecraft.client.gui.screens.Screen.hasAltDown() ? 1.0 : scaleStep());
        transformSelection(xform -> {
            Vector3f scale = new Vector3f(xform.scale());
            switch (this.axis) {
                case X -> scale.x = clampScale(scale.x + delta);
                case Y -> scale.y = clampScale(scale.y + delta);
                case Z -> scale.z = clampScale(scale.z + delta);
                case FREE -> {
                    scale.x = clampScale(scale.x + delta);
                    scale.y = clampScale(scale.y + delta);
                    scale.z = clampScale(scale.z + delta);
                }
            }
            return xform.withScale(scale);
        });
    }

    public void mirror() {
        int axisIndex = switch (this.axis) {
            case Y -> 1;
            case Z -> 2;
            default -> 0;
        };
        Map<Integer, DisplayXform> before = currentXforms();
        if (before.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        Vec3 pivot = centerOfXforms(before.values());
        Map<Integer, DisplayXform> after = new LinkedHashMap<>();
        before.forEach((id, xform) -> after.put(id, xform.mirroredAround(pivot, axisIndex)));
        applyAndRecord(before, after);
    }

    /** Keeps a shrinking display from collapsing to a non-invertible, unclickable sliver. */
    private static float clampScale(double value) {
        if (value >= 0.0) {
            return (float) Math.max(value, MIN_SCALE);
        }
        return (float) Math.min(value, -MIN_SCALE);
    }

    private void transformSelection(UnaryOperator<DisplayXform> operator) {
        Map<Integer, DisplayXform> before = currentXforms();
        if (before.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        Map<Integer, DisplayXform> after = new LinkedHashMap<>();
        before.forEach((id, xform) -> after.put(id, operator.apply(xform)));
        applyAndRecord(before, after);
    }

    private void applyAndRecord(Map<Integer, DisplayXform> before, Map<Integer, DisplayXform> after) {
        sendXforms(after);
        pushUndo(new UndoEntry.Transform(before, after));
    }

    // ---------------------------------------------------------------- placing

    public void placeAtCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        ItemStack palette = mc.player.getOffhandItem();
        if (palette.isEmpty()) {
            feedback(Component.translatable("aebd.message.no_palette"));
            return;
        }
        Vec3 center = placementCenter(mc);
        DisplayData data = palette.getItem() instanceof BlockItem blockItem
                ? DisplayData.ofBlock(blockItem.getBlock().defaultBlockState(), DisplayXform.atCenter(center))
                : DisplayData.ofItem(palette, DisplayXform.atCenter(center));
        sendCreate(List.of(data), ids -> {
            setSelection(ids);
            pushUndo(new UndoEntry.Create(List.of(data), ids));
        });
    }

    private Vec3 placementCenter(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
            return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        Vec3 loose = mc.player.getEyePosition().add(mc.player.getViewVector(1.0f).scale(4.0));
        BlockPos pos = BlockPos.containing(loose);
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- clipboard

    public void copy() {
        List<DisplayData> captured = captureSelection();
        if (captured.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        this.clipboard.clear();
        this.clipboard.addAll(captured);
        feedback(Component.translatable("aebd.message.copied", captured.size()));
    }

    public void cut() {
        copy();
        delete();
    }

    /** Pastes the copied displays back at exactly the positions they were copied from. */
    public void paste() {
        if (this.clipboard.isEmpty()) {
            feedback(Component.translatable("aebd.message.clipboard_empty"));
            return;
        }
        List<DisplayData> pasted = List.copyOf(this.clipboard);
        sendCreate(pasted, ids -> {
            setSelection(ids);
            pushUndo(new UndoEntry.Create(pasted, ids));
            feedback(Component.translatable("aebd.message.pasted", pasted.size()));
        });
    }

    public void duplicate() {
        List<DisplayData> captured = captureSelection();
        if (captured.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        sendCreate(captured, ids -> {
            setSelection(ids);
            pushUndo(new UndoEntry.Create(captured, ids));
            feedback(Component.translatable("aebd.message.duplicated", captured.size()));
        });
    }

    public void delete() {
        List<DisplayData> captured = captureSelection();
        if (captured.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<>(this.selection);
        sendDelete(ids);
        pushUndo(new UndoEntry.Delete(captured, ids));
        this.selection.clear();
        feedback(Component.translatable("aebd.message.deleted", ids.size()));
    }

    private List<DisplayData> captureSelection() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        List<DisplayData> captured = new ArrayList<>();
        for (int id : this.selection) {
            Display display = ClientDisplays.displayById(level, id);
            DisplayData data = display == null ? null : ClientDisplays.dataOf(display);
            if (data != null) {
                DisplayXform predicted = this.preview.get(id);
                captured.add(predicted == null ? data : data.withXform(predicted));
            }
        }
        return captured;
    }

    // ---------------------------------------------------------------- properties

    public void applyProps(DisplayProps props) {
        Level level = Minecraft.getInstance().level;
        if (level == null || this.selection.isEmpty()) {
            return;
        }
        Map<Integer, DisplayProps> before = new LinkedHashMap<>();
        for (int id : this.selection) {
            Display display = ClientDisplays.displayById(level, id);
            if (display != null) {
                before.put(id, ClientDisplays.propsOf(display));
            }
        }
        if (before.isEmpty()) {
            return;
        }
        sendProps(new ArrayList<>(before.keySet()), props);
        pushUndo(new UndoEntry.Properties(before, props));
    }

    public void replaceSelectedBlocks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || this.selection.isEmpty()) {
            feedback(Component.translatable("aebd.message.nothing_selected"));
            return;
        }
        if (!(mc.player.getOffhandItem().getItem() instanceof BlockItem blockItem)) {
            feedback(Component.translatable("aebd.message.no_block_palette"));
            return;
        }
        List<Integer> ids = new ArrayList<>();
        for (int id : this.selection) {
            if (ClientDisplays.displayById(mc.level, id) instanceof Display.BlockDisplay) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            feedback(Component.translatable("aebd.message.no_block_displays"));
            return;
        }
        for (int start = 0; start < ids.size(); start += CHUNK) {
            int end = Math.min(ids.size(), start + CHUNK);
            PacketDistributor.sendToServer(new AEBDPayloads.BlockDisplays(
                    new ArrayList<>(ids.subList(start, end)), blockItem.getBlock().defaultBlockState()));
        }
        feedback(Component.translatable("aebd.message.blocks_replaced", ids.size()));
    }

    /** Writes an absolute transform, used by the numeric editor screen. */
    public void applyXformToSelection(UnaryOperator<DisplayXform> operator) {
        transformSelection(operator);
    }

    // ---------------------------------------------------------------- history

    public void undo() {
        UndoEntry entry = this.undoStack.pollLast();
        if (entry == null) {
            feedback(Component.translatable("aebd.message.nothing_to_undo"));
            return;
        }
        entry.undo(this);
        this.redoStack.addLast(entry);
    }

    public void redo() {
        UndoEntry entry = this.redoStack.pollLast();
        if (entry == null) {
            feedback(Component.translatable("aebd.message.nothing_to_redo"));
            return;
        }
        entry.redo(this);
        this.undoStack.addLast(entry);
    }

    private void pushUndo(UndoEntry entry) {
        this.undoStack.addLast(entry);
        this.redoStack.clear();
        while (this.undoStack.size() > HISTORY_LIMIT) {
            this.undoStack.pollFirst();
        }
    }

    public int resolve(int entityId) {
        Integer next = this.idRemap.get(entityId);
        for (int guard = 0; next != null && guard < 32; guard++) {
            entityId = next;
            next = this.idRemap.get(entityId);
        }
        return entityId;
    }

    public List<Integer> resolveAll(Collection<Integer> ids) {
        return ids.stream().map(this::resolve).toList();
    }

    public Map<Integer, DisplayXform> resolveKeys(Map<Integer, DisplayXform> source) {
        Map<Integer, DisplayXform> out = new LinkedHashMap<>();
        source.forEach((id, xform) -> out.put(resolve(id), xform));
        return out;
    }

    /** Respawns previously captured displays and points stale history ids at the new entities. */
    public void recreate(List<DisplayData> data, List<Integer> previousIds) {
        sendCreate(data, newIds -> {
            for (int i = 0; i < previousIds.size() && i < newIds.size(); i++) {
                this.idRemap.put(resolve(previousIds.get(i)), newIds.get(i));
            }
            setSelection(newIds);
        });
    }

    // ---------------------------------------------------------------- packets

    public void sendXforms(Map<Integer, DisplayXform> xforms) {
        if (xforms.isEmpty()) {
            return;
        }
        this.preview.putAll(xforms);
        List<Integer> ids = new ArrayList<>(xforms.size());
        List<DisplayXform> values = new ArrayList<>(xforms.size());
        xforms.forEach((id, xform) -> {
            ids.add(id);
            values.add(xform);
        });
        for (int start = 0; start < ids.size(); start += CHUNK) {
            int end = Math.min(ids.size(), start + CHUNK);
            PacketDistributor.sendToServer(new AEBDPayloads.TransformDisplays(
                    new ArrayList<>(ids.subList(start, end)),
                    new ArrayList<>(values.subList(start, end))));
        }
    }

    public void sendProps(List<Integer> ids, DisplayProps props) {
        if (ids.isEmpty()) {
            return;
        }
        for (int start = 0; start < ids.size(); start += CHUNK) {
            int end = Math.min(ids.size(), start + CHUNK);
            PacketDistributor.sendToServer(new AEBDPayloads.PropsDisplays(
                    new ArrayList<>(ids.subList(start, end)), props));
        }
    }

    public void sendDelete(List<Integer> ids) {
        if (ids.isEmpty()) {
            return;
        }
        ids.forEach(this.preview::remove);
        for (int start = 0; start < ids.size(); start += CHUNK) {
            int end = Math.min(ids.size(), start + CHUNK);
            PacketDistributor.sendToServer(new AEBDPayloads.DeleteDisplays(
                    new ArrayList<>(ids.subList(start, end))));
        }
    }

    public void sendCreate(List<DisplayData> data, Consumer<List<Integer>> onSpawned) {
        if (data.isEmpty()) {
            return;
        }
        if (this.pending.size() > PENDING_LIMIT) {
            this.pending.clear();
        }
        List<Integer> collected = new ArrayList<>();
        for (int start = 0; start < data.size(); start += CHUNK) {
            int end = Math.min(data.size(), start + CHUNK);
            int requestId = this.nextRequestId++;
            this.pending.put(requestId, ids -> {
                collected.addAll(ids);
                onSpawned.accept(collected);
            });
            PacketDistributor.sendToServer(new AEBDPayloads.CreateDisplays(
                    requestId, new ArrayList<>(data.subList(start, end))));
        }
    }

    void onSelectionResponse(int requestId, List<Integer> ids) {
        Consumer<List<Integer>> callback = this.pending.remove(requestId);
        if (callback != null) {
            callback.accept(ids);
        } else {
            setSelection(ids);
        }
    }

    // ---------------------------------------------------------------- helpers

    public Map<Integer, DisplayXform> currentXforms() {
        Map<Integer, DisplayXform> out = new LinkedHashMap<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return out;
        }
        for (int id : this.selection) {
            Display display = ClientDisplays.displayById(level, id);
            DisplayXform actual = display == null ? null : ClientDisplays.xformOf(display);
            if (actual == null) {
                continue;
            }
            DisplayXform predicted = this.preview.get(id);
            out.put(id, predicted == null ? actual : predicted);
        }
        return out;
    }

    @Nullable
    public DisplayKind kindOfFirstSelected() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (int id : this.selection) {
            DisplayKind kind = DisplayKind.of(ClientDisplays.displayById(level, id));
            if (kind != null) {
                return kind;
            }
        }
        return null;
    }

    public static Vec3 centerOfXforms(Collection<DisplayXform> xforms) {
        return centerOfPoints(xforms.stream().map(DisplayXform::center).toList());
    }

    public static Vec3 centerOfPoints(Collection<Vec3> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Vec3 point : points) {
            x += point.x;
            y += point.y;
            z += point.z;
        }
        int count = points.size();
        return count == 0 ? Vec3.ZERO : new Vec3(x / count, y / count, z / count);
    }

    private static void feedback(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }
}
