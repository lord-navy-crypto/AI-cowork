package dev.swarmmobs.registry;

import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.colony.SwarmNestCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The persistent colony anchor is NOT a real bee hive or ant nest. */
public final class SwarmNestBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(SwarmMobs.MOD_ID);

    public static final DeferredBlock<Block> NEST_CORE = BLOCKS.register(
            "nest_core",
            () -> new SwarmNestCoreBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GRAVEL)
                    .lightLevel(state -> 3))
    );

    private SwarmNestBlocks() {}
}
