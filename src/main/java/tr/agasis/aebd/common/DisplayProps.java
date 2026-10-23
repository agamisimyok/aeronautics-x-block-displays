package tr.agasis.aebd.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * The non-geometric knobs vanilla exposes on a display entity.
 *
 * <p>{@code brightnessBlock}/{@code brightnessSky} of {@code -1} mean "no override, use world
 * light", matching the vanilla convention for {@code glowColor} too.
 */
public record DisplayProps(
        byte billboard,
        int brightnessBlock,
        int brightnessSky,
        float viewRange,
        float shadowRadius,
        float shadowStrength,
        int glowColor,
        byte itemTransform) {

    public static final DisplayProps DEFAULT = new DisplayProps(
            (byte) Display.BillboardConstraints.FIXED.ordinal(),
            -1, -1,
            1.0f,
            0.0f,
            1.0f,
            -1,
            (byte) ItemDisplayContext.FIXED.getId());

    public static final StreamCodec<ByteBuf, DisplayProps> STREAM_CODEC = StreamCodec.of(
            (buf, props) -> {
                buf.writeByte(props.billboard);
                buf.writeInt(props.brightnessBlock);
                buf.writeInt(props.brightnessSky);
                buf.writeFloat(props.viewRange);
                buf.writeFloat(props.shadowRadius);
                buf.writeFloat(props.shadowStrength);
                buf.writeInt(props.glowColor);
                buf.writeByte(props.itemTransform);
            },
            buf -> new DisplayProps(
                    buf.readByte(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readByte()));

    public boolean hasBrightnessOverride() {
        return this.brightnessBlock >= 0 || this.brightnessSky >= 0;
    }

    public Display.BillboardConstraints billboardConstraints() {
        Display.BillboardConstraints[] values = Display.BillboardConstraints.values();
        return values[Math.floorMod(this.billboard, values.length)];
    }

    public ItemDisplayContext itemDisplayContext() {
        return ItemDisplayContext.BY_ID.apply(this.itemTransform);
    }

    public DisplayProps withBillboard(byte value) {
        return new DisplayProps(value, this.brightnessBlock, this.brightnessSky, this.viewRange,
                this.shadowRadius, this.shadowStrength, this.glowColor, this.itemTransform);
    }

    public DisplayProps withBrightness(int block, int sky) {
        return new DisplayProps(this.billboard, block, sky, this.viewRange,
                this.shadowRadius, this.shadowStrength, this.glowColor, this.itemTransform);
    }

    public DisplayProps withGlowColor(int value) {
        return new DisplayProps(this.billboard, this.brightnessBlock, this.brightnessSky, this.viewRange,
                this.shadowRadius, this.shadowStrength, value, this.itemTransform);
    }

    public DisplayProps withViewRange(float value) {
        return new DisplayProps(this.billboard, this.brightnessBlock, this.brightnessSky, value,
                this.shadowRadius, this.shadowStrength, this.glowColor, this.itemTransform);
    }

    public DisplayProps withShadow(float radius, float strength) {
        return new DisplayProps(this.billboard, this.brightnessBlock, this.brightnessSky, this.viewRange,
                radius, strength, this.glowColor, this.itemTransform);
    }

    public DisplayProps withItemTransform(byte value) {
        return new DisplayProps(this.billboard, this.brightnessBlock, this.brightnessSky, this.viewRange,
                this.shadowRadius, this.shadowStrength, this.glowColor, value);
    }
}
