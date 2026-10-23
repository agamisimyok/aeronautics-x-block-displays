package tr.agasis.aebd.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import tr.agasis.aebd.aeronauticsblockdisplays;
import tr.agasis.aebd.common.DisplayData;
import tr.agasis.aebd.common.DisplayProps;
import tr.agasis.aebd.common.DisplayXform;

/** Every packet the editor sends. */
public final class AEBDPayloads {
    /**
     * Upper bound on entries in one packet. Custom payloads are capped at 32767 bytes on the wire,
     * so the client chunks large operations; this is the server-side sanity check.
     */
    public static final int MAX_BATCH = 512;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(aeronauticsblockdisplays.MODID, path);
    }

    private static int readCount(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BATCH) {
            throw new DecoderException("Display batch size out of range: " + count);
        }
        return count;
    }

    /** Spawn new displays. The server answers with {@link Selection} carrying the new entity ids. */
    public record CreateDisplays(int requestId, List<DisplayData> displays) implements CustomPacketPayload {
        public static final Type<CreateDisplays> TYPE = new Type<>(id("create_displays"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CreateDisplays> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.requestId);
                    buf.writeVarInt(payload.displays.size());
                    for (DisplayData data : payload.displays) {
                        DisplayData.STREAM_CODEC.encode(buf, data);
                    }
                },
                buf -> {
                    int requestId = buf.readVarInt();
                    int count = readCount(buf);
                    List<DisplayData> displays = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        displays.add(DisplayData.STREAM_CODEC.decode(buf));
                    }
                    return new CreateDisplays(requestId, displays);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Move, rotate or resize existing displays. Entries pair up by index. */
    public record TransformDisplays(List<Integer> ids, List<DisplayXform> xforms) implements CustomPacketPayload {
        public static final Type<TransformDisplays> TYPE = new Type<>(id("transform_displays"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TransformDisplays> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    int count = Math.min(payload.ids.size(), payload.xforms.size());
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buf.writeVarInt(payload.ids.get(i));
                        DisplayXform.STREAM_CODEC.encode(buf, payload.xforms.get(i));
                    }
                },
                buf -> {
                    int count = readCount(buf);
                    List<Integer> ids = new ArrayList<>(count);
                    List<DisplayXform> xforms = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readVarInt());
                        xforms.add(DisplayXform.STREAM_CODEC.decode(buf));
                    }
                    return new TransformDisplays(ids, xforms);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Overwrite the non-geometric settings of existing displays. */
    public record PropsDisplays(List<Integer> ids, DisplayProps props) implements CustomPacketPayload {
        public static final Type<PropsDisplays> TYPE = new Type<>(id("props_displays"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PropsDisplays> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.ids.size());
                    for (int entityId : payload.ids) {
                        buf.writeVarInt(entityId);
                    }
                    DisplayProps.STREAM_CODEC.encode(buf, payload.props);
                },
                buf -> {
                    int count = readCount(buf);
                    List<Integer> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readVarInt());
                    }
                    return new PropsDisplays(ids, DisplayProps.STREAM_CODEC.decode(buf));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BlockDisplays(List<Integer> ids, BlockState state) implements CustomPacketPayload {
        public static final Type<BlockDisplays> TYPE = new Type<>(id("block_displays"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlockDisplays> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.ids.size());
                    for (int entityId : payload.ids) {
                        buf.writeVarInt(entityId);
                    }
                    buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(payload.state));
                },
                buf -> {
                    int count = readCount(buf);
                    List<Integer> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readVarInt());
                    }
                    BlockState state = Block.BLOCK_STATE_REGISTRY.byId(buf.readVarInt());
                    return new BlockDisplays(ids, state == null ? Blocks.AIR.defaultBlockState() : state);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Remove displays from the world. */
    public record DeleteDisplays(List<Integer> ids) implements CustomPacketPayload {
        public static final Type<DeleteDisplays> TYPE = new Type<>(id("delete_displays"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteDisplays> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.ids.size());
                    for (int entityId : payload.ids) {
                        buf.writeVarInt(entityId);
                    }
                },
                buf -> {
                    int count = readCount(buf);
                    List<Integer> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readVarInt());
                    }
                    return new DeleteDisplays(ids);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server's answer to {@link CreateDisplays}: the entity ids that were actually spawned. */
    public record Selection(int requestId, List<Integer> ids) implements CustomPacketPayload {
        public static final Type<Selection> TYPE = new Type<>(id("selection"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Selection> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.requestId);
                    buf.writeVarInt(payload.ids.size());
                    for (int entityId : payload.ids) {
                        buf.writeVarInt(entityId);
                    }
                },
                buf -> {
                    int requestId = buf.readVarInt();
                    int count = readCount(buf);
                    List<Integer> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readVarInt());
                    }
                    return new Selection(requestId, ids);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server-authoritative attachment pose used for zero-lag client-side rendering. */
    public record AttachmentSync(int entityId, boolean attached, UUID subLevelId, DisplayXform localXform)
            implements CustomPacketPayload {
        public static final Type<AttachmentSync> TYPE = new Type<>(id("attachment_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentSync> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.entityId);
                    buf.writeBoolean(payload.attached);
                    buf.writeUUID(payload.subLevelId);
                    DisplayXform.STREAM_CODEC.encode(buf, payload.localXform);
                },
                buf -> new AttachmentSync(buf.readVarInt(), buf.readBoolean(), buf.readUUID(),
                        DisplayXform.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private AEBDPayloads() {}
}
