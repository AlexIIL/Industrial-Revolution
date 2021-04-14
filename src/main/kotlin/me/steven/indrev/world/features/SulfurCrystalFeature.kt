package me.steven.indrev.world.features

import com.mojang.serialization.Codec
import me.steven.indrev.blocks.misc.SulfurCrystalBlock
import me.steven.indrev.registry.IRBlockRegistry
import me.steven.indrev.utils.any
import me.steven.indrev.utils.forEach
import net.minecraft.block.Blocks
import net.minecraft.block.Material
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import java.util.*

class SulfurCrystalFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(
        world: StructureWorldAccess?,
        chunkGenerator: ChunkGenerator?,
        random: Random,
        blockPos: BlockPos?,
        featureConfig: DefaultFeatureConfig?
    ): Boolean {

        val mutablePos = BlockPos.Mutable()
        val coveredArea = Box(blockPos).expand(8.0, 8.0, 8.0)
        val isNearLava = coveredArea.any { x, y, z ->
            mutablePos.set(x, y, z)
            world?.getBlockState(mutablePos)?.isOf(Blocks.LAVA) == true
        }
        if (!isNearLava) return false
        coveredArea.forEach { x, y, z ->
            mutablePos.set(x, y, z)
            DIRECTIONS_LIST.shuffled(random).forEach { dir ->
                val blockState = world?.getBlockState(mutablePos)
                val pos = mutablePos.offset(dir)
                val airState = world?.getBlockState(pos)
                if (blockState?.material == Material.STONE && airState?.isAir == true) {
                    world.setBlockState(pos, IRBlockRegistry.SULFUR_CRYSTAL_CLUSTER.defaultState.with(SulfurCrystalBlock.FACING, dir), 2)
                    return true
                }
            }
        }

        return false
    }

    companion object {
        private val DIRECTIONS_LIST = Direction.values().toMutableList()
    }
}