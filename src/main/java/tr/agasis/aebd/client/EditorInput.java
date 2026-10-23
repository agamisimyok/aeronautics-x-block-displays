package tr.agasis.aebd.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import tr.agasis.aebd.aeronauticsblockdisplays;
import tr.agasis.aebd.client.screen.DisplayPropsScreen;

/** Routes keyboard and mouse input to the editor while the wand is armed. */
@EventBusSubscriber(modid = aeronauticsblockdisplays.MODID, value = Dist.CLIENT)
public final class EditorInput {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EditorSession session = EditorSession.INSTANCE;
        session.tick();
        if (!EditorSession.active()) {
            AEBDKeys.ALL.forEach(EditorInput::drain);
            return;
        }
        handleKeys(session);
    }

    private static void drain(KeyMapping mapping) {
        while (mapping.consumeClick()) {
            // Discarded: the wand is not armed, so this press is not ours to act on.
        }
    }

    private static void handleKeys(EditorSession session) {
        Minecraft mc = Minecraft.getInstance();

        while (AEBDKeys.GRAB.consumeClick()) {
            session.toggleGrab();
        }
        while (AEBDKeys.MODE_MOVE.consumeClick()) {
            session.setGizmoMode(GizmoMode.MOVE);
        }
        while (AEBDKeys.MODE_ROTATE.consumeClick()) {
            session.setGizmoMode(GizmoMode.ROTATE);
        }
        while (AEBDKeys.MODE_SCALE.consumeClick()) {
            session.setGizmoMode(GizmoMode.SCALE);
        }
        // Shift makes the axis keys a plane lock (the other two axes), Blender-style.
        while (AEBDKeys.AXIS_X.consumeClick()) {
            session.toggleAxis(EditorSession.Axis.X, Screen.hasShiftDown());
        }
        while (AEBDKeys.AXIS_Y.consumeClick()) {
            session.toggleAxis(EditorSession.Axis.Y, Screen.hasShiftDown());
        }
        while (AEBDKeys.AXIS_Z.consumeClick()) {
            session.toggleAxis(EditorSession.Axis.Z, Screen.hasShiftDown());
        }
        while (AEBDKeys.ROTATE_CCW.consumeClick()) {
            session.rotate(-1);
        }
        while (AEBDKeys.ROTATE_CW.consumeClick()) {
            session.rotate(1);
        }
        while (AEBDKeys.SCALE_DOWN.consumeClick()) {
            session.scale(-1);
        }
        while (AEBDKeys.SCALE_UP.consumeClick()) {
            session.scale(1);
        }
        while (AEBDKeys.NUDGE_PLUS.consumeClick()) {
            session.nudge(1);
        }
        while (AEBDKeys.NUDGE_MINUS.consumeClick()) {
            session.nudge(-1);
        }
        while (AEBDKeys.PRECISION_DOWN.consumeClick()) {
            session.stepPrecision(-1);
        }
        while (AEBDKeys.PRECISION_UP.consumeClick()) {
            session.stepPrecision(1);
        }
        while (AEBDKeys.MIRROR.consumeClick()) {
            session.mirror();
        }
        while (AEBDKeys.TOGGLE_SNAP.consumeClick()) {
            session.toggleSnapping();
        }
        while (AEBDKeys.TOGGLE_LOCAL_AXES.consumeClick()) {
            session.toggleLocalAxes();
        }
        while (AEBDKeys.REPLACE_BLOCK.consumeClick()) {
            session.replaceSelectedBlocks();
        }
        while (AEBDKeys.DESELECT.consumeClick()) {
            session.clearSelection();
        }
        while (AEBDKeys.DELETE.consumeClick()) {
            session.delete();
        }
        while (AEBDKeys.COPY.consumeClick()) {
            session.copy();
        }
        while (AEBDKeys.CUT.consumeClick()) {
            session.cut();
        }
        while (AEBDKeys.PASTE.consumeClick()) {
            session.paste();
        }
        while (AEBDKeys.DUPLICATE.consumeClick()) {
            session.duplicate();
        }
        while (AEBDKeys.UNDO.consumeClick()) {
            session.undo();
        }
        while (AEBDKeys.REDO.consumeClick()) {
            session.redo();
        }
        while (AEBDKeys.SELECT_ALL.consumeClick()) {
            session.selectAllNearby();
        }
        while (AEBDKeys.PROPERTIES.consumeClick()) {
            if (!session.selection().isEmpty()) {
                mc.setScreen(new DisplayPropsScreen());
            }
        }
    }

    /**
     * Takes over attack and use while armed. Sneak + use is deliberately left alone so the wand's own
     * toggle still reaches the server and can disarm the tool.
     */
    @SubscribeEvent
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        EditorSession session = EditorSession.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!EditorSession.active() || mc.player == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (event.isAttack()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            if (session.grabbing()) {
                session.confirmGrab();
            } else if (session.gizmoMode() != GizmoMode.NONE && session.hoveredHandle() != Gizmo.NONE) {
                session.beginGizmoDrag(Screen.hasShiftDown());
            } else {
                session.clickSelect(mc.player.isShiftKeyDown());
            }
            return;
        }

        if (event.isUseItem()) {
            if (session.grabbing()) {
                event.setSwingHand(false);
                event.setCanceled(true);
                session.cancelGrab();
                return;
            }
            if (mc.player.isShiftKeyDown()) {
                return;
            }
            event.setSwingHand(false);
            event.setCanceled(true);
            session.placeAtCursor();
        }
    }

    /** Middle-click snaps the current selection to whatever the crosshair is pointing at. */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        EditorSession session = EditorSession.INSTANCE;
        if (session.draggingGizmo() && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getAction() == GLFW.GLFW_RELEASE) {
            session.confirmGizmoDrag();
            event.setCanceled(true);
            return;
        }
        if (session.draggingGizmo() && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && event.getAction() == GLFW.GLFW_PRESS) {
            session.cancelGizmoDrag();
            event.setCanceled(true);
            return;
        }
        if (!EditorSession.active() || event.getAction() != GLFW.GLFW_PRESS
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return;
        }
        if (!session.selection().isEmpty()) {
            session.placeAtLook();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        EditorSession session = EditorSession.INSTANCE;
        if (!EditorSession.active()) {
            return;
        }
        if (session.grabbing()) {
            session.adjustGrabDistance(event.getScrollDeltaY());
            event.setCanceled(true);
        } else if (Screen.hasControlDown() && event.getScrollDeltaY() != 0.0) {
            session.stepPrecision((int) Math.signum(event.getScrollDeltaY()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EditorSession.INSTANCE.reset();
    }

    private EditorInput() {}
}
