package tr.agasis.aebd.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tr.agasis.aebd.aeronauticsblockdisplays;
import tr.agasis.aebd.item.DisplayEditorItem;

public final class AEBDItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(aeronauticsblockdisplays.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, aeronauticsblockdisplays.MODID);

    public static final DeferredItem<Item> DISPLAY_EDITOR = ITEMS.register("display_editor",
            () -> new DisplayEditorItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeronauticsblockdisplays"))
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .icon(() -> DISPLAY_EDITOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(DISPLAY_EDITOR.get()))
                    .build());

    private AEBDItems() {}
}
