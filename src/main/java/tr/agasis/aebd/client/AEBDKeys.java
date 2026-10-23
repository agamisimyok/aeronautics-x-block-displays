package tr.agasis.aebd.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import tr.agasis.aebd.aeronauticsblockdisplays;

@EventBusSubscriber(modid = aeronauticsblockdisplays.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AEBDKeys {

    public static final String CATEGORY = "key.categories.aeronauticsblockdisplays";

    public static final KeyMapping GRAB = key("grab", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping MODE_MOVE = key("mode_move", InputConstants.KEY_G);
    public static final KeyMapping MODE_ROTATE = key("mode_rotate", InputConstants.KEY_R);
    public static final KeyMapping MODE_SCALE = key("mode_scale", InputConstants.KEY_B);
    public static final KeyMapping AXIS_X = key("axis_x", InputConstants.KEY_X);
    public static final KeyMapping AXIS_Y = key("axis_y", InputConstants.KEY_Y);
    public static final KeyMapping AXIS_Z = key("axis_z", InputConstants.KEY_Z);
    public static final KeyMapping ROTATE_CCW = key("rotate_ccw", InputConstants.KEY_COMMA);
    public static final KeyMapping ROTATE_CW = key("rotate_cw", InputConstants.KEY_PERIOD);
    public static final KeyMapping SCALE_DOWN = key("scale_down", InputConstants.KEY_MINUS);
    public static final KeyMapping SCALE_UP = key("scale_up", InputConstants.KEY_EQUALS);
    public static final KeyMapping NUDGE_PLUS = key("nudge_plus", InputConstants.KEY_UP);
    public static final KeyMapping NUDGE_MINUS = key("nudge_minus", InputConstants.KEY_DOWN);
    public static final KeyMapping PRECISION_DOWN = key("precision_down", InputConstants.KEY_LBRACKET);
    public static final KeyMapping PRECISION_UP = key("precision_up", InputConstants.KEY_RBRACKET);
    public static final KeyMapping MIRROR = key("mirror", InputConstants.KEY_M);
    public static final KeyMapping TOGGLE_SNAP = key("toggle_snap", InputConstants.KEY_H);
    public static final KeyMapping TOGGLE_LOCAL_AXES = key("toggle_local_axes", InputConstants.KEY_L);
    public static final KeyMapping REPLACE_BLOCK = key("replace_block", InputConstants.KEY_K);
    public static final KeyMapping PROPERTIES = key("properties", InputConstants.KEY_N);
    public static final KeyMapping DESELECT = key("deselect", InputConstants.KEY_BACKSLASH);
    public static final KeyMapping DELETE = key("delete", InputConstants.KEY_DELETE);

    public static final KeyMapping COPY = key("copy", KeyModifier.CONTROL, InputConstants.KEY_C);
    public static final KeyMapping CUT = key("cut", KeyModifier.CONTROL, InputConstants.KEY_X);
    public static final KeyMapping PASTE = key("paste", KeyModifier.CONTROL, InputConstants.KEY_V);
    public static final KeyMapping DUPLICATE = key("duplicate", KeyModifier.CONTROL, InputConstants.KEY_D);
    public static final KeyMapping UNDO = key("undo", KeyModifier.CONTROL, InputConstants.KEY_Z);
    public static final KeyMapping REDO = key("redo", KeyModifier.CONTROL, InputConstants.KEY_Y);
    public static final KeyMapping SELECT_ALL = key("select_all", KeyModifier.CONTROL, InputConstants.KEY_A);

    /**
     * Registration order plus the drain list. Presses queue up inside {@link KeyMapping} whether or
     * not the wand is armed, so idle ticks have to throw them away rather than let them pile up and
     * all fire at once on the next toggle.
     */
    public static final java.util.List<KeyMapping> ALL = java.util.List.of(
            GRAB, MODE_MOVE, MODE_ROTATE, MODE_SCALE,
            AXIS_X, AXIS_Y, AXIS_Z, ROTATE_CCW, ROTATE_CW, SCALE_DOWN, SCALE_UP,
            NUDGE_PLUS, NUDGE_MINUS, PRECISION_DOWN, PRECISION_UP, MIRROR, TOGGLE_SNAP,
            TOGGLE_LOCAL_AXES, REPLACE_BLOCK,
            PROPERTIES, DESELECT, DELETE, COPY, CUT, PASTE, DUPLICATE, UNDO, REDO, SELECT_ALL);

    private static KeyMapping key(String name, int keyCode) {
        return key(name, KeyModifier.NONE, keyCode);
    }

    private static KeyMapping key(String name, KeyModifier modifier, int keyCode) {
        return new KeyMapping(
                "key." + aeronauticsblockdisplays.MODID + "." + name,
                KeyConflictContext.IN_GAME,
                modifier,
                InputConstants.Type.KEYSYM,
                keyCode,
                CATEGORY);
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        ALL.forEach(event::register);
    }

    private AEBDKeys() {}
}
