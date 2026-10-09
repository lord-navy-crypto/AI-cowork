package dev.swarmmobs.registry;

import dev.swarmmobs.SwarmMobs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A persistent world-space marker for ant-like, decentralized colony work.
 * It has no per-tick block entity and therefore no passive block-entity load.
 */
public final class SwarmNestBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(SwarmMobs.MOD_ID);

    public static final DeferredBlock<Block> NEST_CORE = BLOCKS.register(
            "nest_core",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GRAVEL)
                    .lightLevel(state -> 3))
    );

    private SwarmNestBlocks() {}
}
