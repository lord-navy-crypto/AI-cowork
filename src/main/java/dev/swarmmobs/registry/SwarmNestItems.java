package dev.swarmmobs.registry;

import dev.swarmmobs.SwarmMobs;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Allows testing a Nest Core with /give swarmmobs:nest_core. */
public final class SwarmNestItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SwarmMobs.MOD_ID);
    static {
        ITEMS.registerSimpleBlockItem("nest_core", SwarmNestBlocks.NEST_CORE);
    }
    private SwarmNestItems() {}
}
