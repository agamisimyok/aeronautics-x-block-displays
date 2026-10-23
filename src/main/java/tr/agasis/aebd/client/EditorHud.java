package tr.agasis.aebd.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import tr.agasis.aebd.aeronauticsblockdisplays;

/** Top-left status panel: what is selected and what the modifier keys will do to it. */
@EventBusSubscriber(modid = aeronauticsblockdisplays.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EditorHud {

    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BACKGROUND = 0xA0000000;

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(aeronauticsblockdisplays.MODID, "editor_hud"),
                EditorHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!EditorSession.active() || mc.player == null || mc.options.hideGui) {
            return;
        }
        EditorSession session = EditorSession.INSTANCE;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("aebd.hud.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        lines.add(Component.translatable("aebd.hud.mode", modeLabel(session)).withStyle(ChatFormatting.WHITE));
        if (session.grabbing()) {
            lines.add(Component.translatable("aebd.hud.grabbing").withStyle(ChatFormatting.YELLOW));
        }
        if (session.draggingGizmo()) {
            lines.add(Component.translatable("aebd.hud.dragging").withStyle(ChatFormatting.YELLOW));
        }
        lines.add(Component.translatable("aebd.hud.selection",
                session.selection().size(), session.clipboardSize()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("aebd.hud.axis", axisLabel(session))
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("aebd.hud.step",
                formatStep(session.moveStep()),
                formatAngle(session.rotateStep()),
                formatStep(session.scaleStep())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(session.snapping() ? "aebd.hud.snap_on" : "aebd.hud.snap_off")
                .withStyle(session.snapping() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        lines.add(Component.translatable(session.localAxes() ? "aebd.hud.axes_local" : "aebd.hud.axes_world")
                .withStyle(session.localAxes() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        lines.add(paletteLine(mc.player.getOffhandItem()));
        lines.addAll(controlHints(session));

        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, mc.font.width(line));
        }
        int boxWidth = width + PADDING * 2;
        int boxHeight = lines.size() * LINE_HEIGHT + PADDING * 2 - 2;
        graphics.fill(MARGIN, MARGIN, MARGIN + boxWidth, MARGIN + boxHeight, BACKGROUND);

        int y = MARGIN + PADDING;
        for (Component line : lines) {
            graphics.drawString(mc.font, line, MARGIN + PADDING, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }
    }

    /**
     * A short cheat-sheet of the core verbs, labelled with the players's actual bound keys so it stays
     * correct after a rebind and across keyboard layouts.
     */
    private static List<Component> controlHints(EditorSession session) {
        List<Component> hints = new ArrayList<>();
        hints.add(Component.empty());
        hints.add(hint("aebd.hud.hint_place"));
        hints.add(hint("aebd.hud.hint_select"));
        hints.add(hint("aebd.hud.hint_modes", AEBDKeys.MODE_MOVE, AEBDKeys.MODE_ROTATE, AEBDKeys.MODE_SCALE));
        if (session.gizmoMode() == GizmoMode.NONE) {
            hints.add(hint("aebd.hud.hint_grab", AEBDKeys.GRAB));
            hints.add(hint("aebd.hud.hint_rotate", AEBDKeys.ROTATE_CCW, AEBDKeys.ROTATE_CW));
            hints.add(hint("aebd.hud.hint_scale", AEBDKeys.SCALE_DOWN, AEBDKeys.SCALE_UP));
        } else {
            hints.add(hint("aebd.hud.hint_drag"));
            hints.add(hint("aebd.hud.hint_place_look"));
        }
        hints.add(hint("aebd.hud.hint_axis", AEBDKeys.AXIS_X, AEBDKeys.AXIS_Y, AEBDKeys.AXIS_Z));
        hints.add(hint("aebd.hud.hint_local_axes", AEBDKeys.TOGGLE_LOCAL_AXES));
        hints.add(hint("aebd.hud.hint_replace_block", AEBDKeys.REPLACE_BLOCK));
        hints.add(hint("aebd.hud.hint_panel", AEBDKeys.PROPERTIES));
        return hints;
    }

    private static Component hint(String key, net.minecraft.client.KeyMapping... mappings) {
        Object[] names = new Object[mappings.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = mappings[i].getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        }
        return Component.translatable(key, names).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component paletteLine(ItemStack offhand) {
        if (offhand.isEmpty()) {
            return Component.translatable("aebd.hud.palette_empty").withStyle(ChatFormatting.RED);
        }
        return Component.translatable("aebd.hud.palette", offhand.getHoverName())
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component axisLabel(EditorSession session) {
        String prefix = session.plane() && session.axis() != EditorSession.Axis.FREE ? "⧉ " : "";
        return switch (session.axis()) {
            case X -> Component.literal(prefix + "X").withStyle(ChatFormatting.RED);
            case Y -> Component.literal(prefix + "Y").withStyle(ChatFormatting.GREEN);
            case Z -> Component.literal(prefix + "Z").withStyle(ChatFormatting.BLUE);
            case FREE -> Component.translatable("aebd.hud.axis_free").withStyle(ChatFormatting.GRAY);
        };
    }

    private static Component modeLabel(EditorSession session) {
        return switch (session.gizmoMode()) {
            case MOVE -> Component.translatable("aebd.hud.mode_move").withStyle(ChatFormatting.AQUA);
            case ROTATE -> Component.translatable("aebd.hud.mode_rotate").withStyle(ChatFormatting.AQUA);
            case SCALE -> Component.translatable("aebd.hud.mode_scale").withStyle(ChatFormatting.AQUA);
            case NONE -> Component.translatable("aebd.hud.mode_none").withStyle(ChatFormatting.GRAY);
        };
    }

    private static String formatStep(double value) {
        if (value >= 1.0) {
            return String.valueOf((int) Math.round(value));
        }
        return "1/" + (int) Math.round(1.0 / value);
    }

    private static String formatAngle(double degrees) {
        return degrees == Math.rint(degrees)
                ? (int) degrees + "°"
                : String.format("%.1f°", degrees);
    }

    private EditorHud() {}
}
