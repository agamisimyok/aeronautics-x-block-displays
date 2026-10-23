package tr.agasis.aebd.net;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tr.agasis.aebd.aeronautics.AttachmentManager;
import tr.agasis.aebd.common.DisplayData;
import tr.agasis.aebd.common.DisplayKind;
import tr.agasis.aebd.common.DisplayOps;
import tr.agasis.aebd.common.DisplayXform;

/** Applies editor packets to the world, after checking the sender is allowed to. */
public final class ServerPayloadHandler {

    /** How far from the player an edit may reach. Builders work at range, but not across the map. */
    private static final double MAX_REACH = 256.0;

    public static void handleCreate(AEBDPayloads.CreateDisplays payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = requireEditor(context);
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            List<Integer> spawned = new ArrayList<>(payload.displays().size());
            for (DisplayData data : payload.displays()) {
                if (!inReach(player, data.center())) {
                    continue;
                }
                Display display = DisplayOps.create(level, data);
                if (display != null) {
                    spawned.add(display.getId());
                }
            }
            PacketDistributor.sendToPlayer(player, new AEBDPayloads.Selection(payload.requestId(), spawned));
        });
    }

    public static void handleTransform(AEBDPayloads.TransformDisplays payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = requireEditor(context);
            if (player == null) {
                return;
            }
            List<Integer> ids = payload.ids();
            List<DisplayXform> xforms = payload.xforms();
            for (int i = 0; i < ids.size() && i < xforms.size(); i++) {
                Display display = resolve(player, ids.get(i));
                DisplayKind kind = DisplayKind.of(display);
                DisplayXform xform = xforms.get(i);
                if (kind != null && inReach(player, xform.center())) {
                    DisplayOps.applyXform(display, kind, xform);
                    AttachmentManager.afterManualTransform(player.serverLevel(), display, kind, xform);
                }
            }
        });
    }

    public static void handleProps(AEBDPayloads.PropsDisplays payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = requireEditor(context);
            if (player == null) {
                return;
            }
            for (int id : payload.ids()) {
                Display display = resolve(player, id);
                DisplayKind kind = DisplayKind.of(display);
                if (kind != null) {
                    DisplayOps.applyProps(display, kind, payload.props());
                }
            }
        });
    }

    public static void handleBlocks(AEBDPayloads.BlockDisplays payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = requireEditor(context);
            if (player == null) {
                return;
            }
            for (int id : payload.ids()) {
                Display display = resolve(player, id);
                if (display instanceof Display.BlockDisplay blockDisplay) {
                    DisplayOps.applyBlockState(blockDisplay, payload.state());
                }
            }
        });
    }

    public static void handleDelete(AEBDPayloads.DeleteDisplays payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = requireEditor(context);
            if (player == null) {
                return;
            }
            for (int id : payload.ids()) {
                Display display = resolve(player, id);
                if (display != null) {
                    display.discard();
                }
            }
        });
    }

    @Nullable
    private static ServerPlayer requireEditor(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return null;
        }
        if (!DisplayOps.canEdit(player)) {
            player.displayClientMessage(Component.translatable("aebd.message.no_permission"), true);
            return null;
        }
        return player;
    }

    @Nullable
    private static Display resolve(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity instanceof Display display && inReach(player, display.position())) {
            return display;
        }
        return null;
    }

    private static boolean inReach(ServerPlayer player, Vec3 point) {
        return player.position().distanceToSqr(point) <= MAX_REACH * MAX_REACH;
    }

    private ServerPayloadHandler() {}
}
