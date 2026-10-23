package tr.agasis.aebd.client;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import tr.agasis.aebd.net.AEBDPayloads;

public final class ClientPayloadHandler {

    public static void handleSelection(AEBDPayloads.Selection payload, IPayloadContext context) {
        context.enqueueWork(() -> EditorSession.INSTANCE.onSelectionResponse(payload.requestId(), payload.ids()));
    }

    public static void handleAttachmentSync(AEBDPayloads.AttachmentSync payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientAttachmentManager.sync(payload));
    }

    private ClientPayloadHandler() {}
}
