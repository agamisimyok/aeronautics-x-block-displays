package tr.agasis.aebd;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import tr.agasis.aebd.aeronautics.AttachmentManager;
import tr.agasis.aebd.registry.AEBDComponents;
import tr.agasis.aebd.registry.AEBDItems;

@Mod(aeronauticsblockdisplays.MODID)
public class aeronauticsblockdisplays {

    public static final String MODID = "aeronauticsblockdisplays";

    public static final Logger LOGGER = LogUtils.getLogger();

    public aeronauticsblockdisplays(IEventBus modEventBus) {
        AEBDComponents.COMPONENTS.register(modEventBus);
        AEBDItems.ITEMS.register(modEventBus);
        AEBDItems.TABS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(AttachmentManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(AttachmentManager::onStartTracking);
    }
}
