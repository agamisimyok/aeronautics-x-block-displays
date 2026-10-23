package tr.agasis.aebd.common;

import javax.annotation.Nullable;

import com.mojang.math.Transformation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side writes to display entities.
 *
 * <p>Vanilla keeps every display setter private, so edits go through the same save/merge/load
 * round-trip the {@code /data} command uses: dump the entity to NBT, overwrite the keys we own, feed
 * it back. {@code saveWithoutId} includes the UUID, so the identity survives the reload.
 */
public final class DisplayOps {

    /** Keeps a stray packet from letting a survival player rewrite the world. */
    public static boolean canEdit(Player player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    @Nullable
    public static Display create(ServerLevelAccessor level, DisplayData data) {
        Display display = data.kind().entityType().create(level.getLevel());
        if (display == null) {
            return null;
        }
        Vec3 center = data.xform().center();
        display.moveTo(center.x, center.y, center.z, 0.0f, 0.0f);
        applyAll(display, data);
        level.addFreshEntity(display);
        return display;
    }

    public static void applyAll(Display display, DisplayData data) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        writeXform(tag, data.xform(), data.kind());
        writeProps(tag, data.props(), data.kind());
        writeContent(tag, display.level(), data);
        reload(display, tag, data.xform().center());
    }

    public static void applyXform(Display display, DisplayKind kind, DisplayXform xform) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        writeXform(tag, xform, kind);
        reload(display, tag, xform.center());
    }

    /** Applies an exact riding pose and full-bright lighting for displays embedded in a sub-level. */
    public static void applyFollowingXform(Display display, DisplayKind kind, DisplayXform xform) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        writeXform(tag, xform, kind);
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put(Display.TAG_BRIGHTNESS, brightness);
        reload(display, tag, xform.center());
    }

    public static void applyProps(Display display, DisplayKind kind, DisplayProps props) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        writeProps(tag, props, kind);
        reload(display, tag, null);
    }

    public static void applyBlockState(Display.BlockDisplay display, BlockState state) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(state));
        reload(display, tag, null);
    }

    private static void reload(Display display, CompoundTag tag, @Nullable Vec3 center) {
        display.load(tag);
        if (center != null) {
            display.teleportTo(center.x, center.y, center.z);
        }
    }

    private static void writeXform(CompoundTag tag, DisplayXform xform, DisplayKind kind) {
        Transformation transformation = xform.toTransformation(kind);
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, transformation)
                .ifSuccess(encoded -> tag.put(Display.TAG_TRANSFORMATION, encoded));
        // Edits should land exactly where the builder put them rather than easing in.
        tag.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, 0);
        tag.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, 0);
        tag.remove(Display.TAG_TRANSFORMATION_START_INTERPOLATION);
    }

    private static void writeProps(CompoundTag tag, DisplayProps props, DisplayKind kind) {
        Display.BillboardConstraints.CODEC.encodeStart(NbtOps.INSTANCE, props.billboardConstraints())
                .ifSuccess(encoded -> tag.put(Display.TAG_BILLBOARD, encoded));
        tag.putFloat(Display.TAG_VIEW_RANGE, Math.max(0.0f, props.viewRange()));
        tag.putFloat(Display.TAG_SHADOW_RADIUS, Math.max(0.0f, props.shadowRadius()));
        tag.putFloat(Display.TAG_SHADOW_STRENGTH, Math.max(0.0f, props.shadowStrength()));
        tag.putInt(Display.TAG_GLOW_COLOR_OVERRIDE, props.glowColor());
        if (props.hasBrightnessOverride()) {
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("block", Mth.clamp(props.brightnessBlock(), 0, 15));
            brightness.putInt("sky", Mth.clamp(props.brightnessSky(), 0, 15));
            tag.put(Display.TAG_BRIGHTNESS, brightness);
        } else {
            tag.remove(Display.TAG_BRIGHTNESS);
        }
        if (kind == DisplayKind.ITEM) {
            ItemDisplayContext.CODEC.encodeStart(NbtOps.INSTANCE, props.itemDisplayContext())
                    .ifSuccess(encoded -> tag.put("item_display", encoded));
        }
    }

    private static void writeContent(CompoundTag tag, Level level, DisplayData data) {
        if (data.kind() == DisplayKind.BLOCK) {
            tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(data.blockState()));
        } else if (!data.item().isEmpty()) {
            tag.put("item", data.item().save(level.registryAccess()));
        } else {
            tag.remove("item");
        }
    }

    private DisplayOps() {}
}
