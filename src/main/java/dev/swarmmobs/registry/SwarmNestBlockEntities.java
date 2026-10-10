package dev.swarmmobs.registry;

import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SwarmNestBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, SwarmMobs.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SwarmNestBlockEntity>> NEST =
            TYPES.register("nest_core", () -> BlockEntityType.Builder.of(
                    SwarmNestBlockEntity::new, SwarmNestBlocks.NEST_CORE.get()
            ).build(null));

    private SwarmNestBlockEntities() {}
}
