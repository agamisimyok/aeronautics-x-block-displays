package tr.agasis.aebd.net;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import tr.agasis.aebd.aeronauticsblockdisplays;
import tr.agasis.aebd.client.ClientPayloadHandler;

@EventBusSubscriber(modid = aeronauticsblockdisplays.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AEBDNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                AEBDPayloads.CreateDisplays.TYPE,
                AEBDPayloads.CreateDisplays.CODEC,
                ServerPayloadHandler::handleCreate);
        registrar.playToServer(
                AEBDPayloads.TransformDisplays.TYPE,
                AEBDPayloads.TransformDisplays.CODEC,
                ServerPayloadHandler::handleTransform);
        registrar.playToServer(
                AEBDPayloads.PropsDisplays.TYPE,
                AEBDPayloads.PropsDisplays.CODEC,
                ServerPayloadHandler::handleProps);
        registrar.playToServer(
                AEBDPayloads.BlockDisplays.TYPE,
                AEBDPayloads.BlockDisplays.CODEC,
                ServerPayloadHandler::handleBlocks);
        registrar.playToServer(
                AEBDPayloads.DeleteDisplays.TYPE,
                AEBDPayloads.DeleteDisplays.CODEC,
                ServerPayloadHandler::handleDelete);
        // Wrapped in a lambda so the client-only handler class is not loaded on a dedicated server.
        registrar.playToClient(
                AEBDPayloads.Selection.TYPE,
                AEBDPayloads.Selection.CODEC,
                (payload, context) -> ClientPayloadHandler.handleSelection(payload, context));
        registrar.playToClient(
                AEBDPayloads.AttachmentSync.TYPE,
                AEBDPayloads.AttachmentSync.CODEC,
                (payload, context) -> ClientPayloadHandler.handleAttachmentSync(payload, context));
    }

    private AEBDNetwork() {}
}
