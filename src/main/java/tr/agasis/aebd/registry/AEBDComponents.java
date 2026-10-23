package tr.agasis.aebd.registry;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tr.agasis.aebd.aeronauticsblockdisplays;

public final class AEBDComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, aeronauticsblockdisplays.MODID);

    /**
     * Whether the editor wand is armed. Stored on the stack so the mode survives relogs and stays in
     * sync with the client for free through normal inventory updates.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> EDITING =
            COMPONENTS.register("editing", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    private AEBDComponents() {}
}
