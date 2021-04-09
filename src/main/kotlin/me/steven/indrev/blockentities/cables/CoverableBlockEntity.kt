package me.steven.indrev.blockentities.cables

import me.steven.indrev.api.machines.Tier
import me.steven.indrev.registry.IRBlockRegistry
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

class CoverableBlockEntity(tier: Tier) :
    BlockEntity(when (tier) {
        Tier.MK1 -> IRBlockRegistry.COVERABLE_BLOCK_ENTITY_TYPE_MK1
        Tier.MK2 -> IRBlockRegistry.COVERABLE_BLOCK_ENTITY_TYPE_MK2
        Tier.MK3 -> IRBlockRegistry.COVERABLE_BLOCK_ENTITY_TYPE_MK3
        Tier.MK4 -> IRBlockRegistry.COVERABLE_BLOCK_ENTITY_TYPE_MK4
        Tier.CREATIVE -> error("no creative cable")
    }), BlockEntityClientSerializable {
    var coverState: BlockState? = null

    override fun fromTag(state: BlockState?, tag: CompoundTag?) {
        if (tag?.contains("cover") == true)
            Registry.BLOCK.getOrEmpty(Identifier(tag.getString("cover")))
                .ifPresent { block -> this.coverState = block.defaultState }
        if (tag?.contains("coverState") == true) {
            BlockState.CODEC.decode(NbtOps.INSTANCE, tag.getCompound("coverState")).result().ifPresent { pair ->
                this.coverState = pair.first
            }
        }
        super.fromTag(state, tag)
    }

    override fun toTag(tag: CompoundTag?): CompoundTag {
        if (this.coverState != null) {
            BlockState.CODEC.encode(this.coverState, NbtOps.INSTANCE, CompoundTag()).result().ifPresent { t ->
                tag?.put("coverState", t)
            }
        }
        return super.toTag(tag)
    }

    override fun fromClientTag(tag: CompoundTag?) {
        if (tag?.contains("cover") == true)
            Registry.BLOCK.getOrEmpty(Identifier(tag.getString("cover")))
                .ifPresent { block -> this.coverState = block.defaultState }
        if (tag?.contains("coverState") == true) {
            BlockState.CODEC.decode(NbtOps.INSTANCE, tag.getCompound("coverState")).result().ifPresent { pair ->
                if (pair.first != null) this.coverState = pair.first
            }
        }
    }

    override fun toClientTag(tag: CompoundTag): CompoundTag {
        if (this.coverState != null) {
            BlockState.CODEC.encode(this.coverState, NbtOps.INSTANCE, CompoundTag()).result().ifPresent { t ->
                tag.put("coverState", t)
            }
        }
        return tag
    }
}