package tr.agasis.aebd.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A complete, self-contained snapshot of one display: enough to recreate it from nothing. */
public record DisplayData(
        DisplayKind kind,
        BlockState blockState,
        ItemStack item,
        DisplayXform xform,
        DisplayProps props) {

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeByte(data.kind.ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, Block.BLOCK_STATE_REGISTRY.getId(data.blockState));
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, data.item);
                DisplayXform.STREAM_CODEC.encode(buf, data.xform);
                DisplayProps.STREAM_CODEC.encode(buf, data.props);
            },
            buf -> {
                DisplayKind kind = DisplayKind.byId(buf.readByte());
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(ByteBufCodecs.VAR_INT.decode(buf));
                ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                DisplayXform xform = DisplayXform.STREAM_CODEC.decode(buf);
                DisplayProps props = DisplayProps.STREAM_CODEC.decode(buf);
                return new DisplayData(kind, state == null ? Blocks.AIR.defaultBlockState() : state, item, xform, props);
            });

    public static DisplayData ofBlock(BlockState state, DisplayXform xform) {
        return new DisplayData(DisplayKind.BLOCK, state, ItemStack.EMPTY, xform, DisplayProps.DEFAULT);
    }

    public static DisplayData ofItem(ItemStack item, DisplayXform xform) {
        return new DisplayData(DisplayKind.ITEM, Blocks.AIR.defaultBlockState(), item.copyWithCount(1), xform, DisplayProps.DEFAULT);
    }

    public DisplayData withXform(DisplayXform newXform) {
        return new DisplayData(this.kind, this.blockState, this.item, newXform, this.props);
    }

    public DisplayData withProps(DisplayProps newProps) {
        return new DisplayData(this.kind, this.blockState, this.item, this.xform, newProps);
    }

    public Vec3 center() {
        return this.xform.center();
    }
}
