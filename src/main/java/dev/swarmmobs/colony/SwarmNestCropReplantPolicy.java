package dev.swarmmobs.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.UUID;

/**
 * Colony's optional renewable farm cycle. No free seeds: a newly dropped
 * physical propagule must be consumed after the valid juvenile crop is
 * placed. Existing unrelated world items are NEVER used as nursery inputs.
 */
public final class SwarmNestCropReplantPolicy {
    public record Plan(BlockState juvenile, Item propagule) {}

    public static Plan plan(BlockState mature, BlockState ground) {
        if (mature == null || ground == null) return null;
        if (ground.is(Blocks.FARMLAND)) {
            if (mature.is(Blocks.WHEAT) && mature.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(mature))
                return new Plan(crop.getStateForAge(0), Items.WHEAT_SEEDS);
            if (mature.is(Blocks.CARROTS) && mature.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(mature))
                return new Plan(crop.getStateForAge(0), Items.CARROT);
            if (mature.is(Blocks.POTATOES) && mature.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(mature))
                return new Plan(crop.getStateForAge(0), Items.POTATO);
            if (mature.is(Blocks.BEETROOTS) && mature.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(mature))
                return new Plan(crop.getStateForAge(0), Items.BEETROOT_SEEDS);
        }
        if (ground.is(Blocks.SOUL_SAND) && mature.is(Blocks.NETHER_WART)
                && mature.getValue(NetherWartBlock.AGE) == 3) {
            return new Plan(Blocks.NETHER_WART.defaultBlockState(), Items.NETHER_WART);
        }
        return null;
    }

    /**
     * Call ONLY after breaking an actual mature crop with normal loot.
     * The set of pre-existing nearby items is sampled before the break,
     * ensuring the nest cannot steal a player's previously dropped seed.
     */
    public static boolean replant(ServerLevel level, BlockPos site,
                                  Plan plan, Set<UUID> preexisting) {
        if (level == null || site == null || plan == null || preexisting == null
                || !level.hasChunkAt(site) || !level.hasChunkAt(site.below())
                || !level.getBlockState(site).isAir()
                || !plan.juvenile().canSurvive(level,site)) return false;
        for (ItemEntity drop : level.getEntitiesOfClass(
                ItemEntity.class,new AABB(site).inflate(1.5),
                entity -> entity.isAlive() && entity.getItem().is(plan.propagule())
                        && !entity.getItem().isEmpty()
                        && !preexisting.contains(entity.getUUID()))) {
            // Persisting a juvenile crop before debiting one REAL drop avoids
            // consuming any seed if placement is rejected by the world.
            if (!level.setBlockAndUpdate(site,plan.juvenile())) return false;
            drop.getItem().shrink(1);
            if (drop.getItem().isEmpty()) drop.discard();
            return true;
        }
        return false;
    }

    private SwarmNestCropReplantPolicy() {}
}
