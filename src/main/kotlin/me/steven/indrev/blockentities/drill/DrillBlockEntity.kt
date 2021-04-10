package me.steven.indrev.blockentities.drill

import me.steven.indrev.blocks.machine.DrillBlock
import me.steven.indrev.gui.screenhandlers.machines.MiningRigDrillScreenHandler
import me.steven.indrev.registry.IRBlockRegistry
import me.steven.indrev.registry.IRItemRegistry
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.block.BlockState
import net.minecraft.block.entity.LootableContainerBlockEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventories
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Tickable
import net.minecraft.util.collection.DefaultedList

class DrillBlockEntity : LootableContainerBlockEntity(IRBlockRegistry.DRILL_BLOCK_ENTITY_TYPE), BlockEntityClientSerializable, ExtendedScreenHandlerFactory, Tickable {
    var inventory: DefaultedList<ItemStack> = DefaultedList.ofSize(1, ItemStack.EMPTY)

    var position: Double = 1.0

    override fun tick() {
        if (world?.isClient == true) return

        if (inventory[0].isEmpty) {
            position = 1.0
            markDirty()
            sync()
        } else if (cachedState[DrillBlock.WORKING]) {
            if (position > 0) position -= 0.01

            else return
            markDirty()
            sync()
        }
    }

    fun setWorkingState(working: Boolean) {
        if (cachedState[DrillBlock.WORKING] != working)
            world?.setBlockState(pos, cachedState.with(DrillBlock.WORKING, working))
    }

    override fun size(): Int = 1

    override fun getContainerName(): Text = TranslatableText("block.indrev.drill")

    override fun createScreenHandler(syncId: Int, playerInventory: PlayerInventory): ScreenHandler {
        return MiningRigDrillScreenHandler(syncId, playerInventory, ScreenHandlerContext.create(world, pos))
    }

    override fun getInvStackList(): DefaultedList<ItemStack> = inventory

    override fun setInvStackList(list: DefaultedList<ItemStack>) {
        inventory = list
    }

    override fun fromTag(state: BlockState?, tag: CompoundTag?) {
        super.fromTag(state, tag)
        inventory = DefaultedList.ofSize(size(), ItemStack.EMPTY)
        if (!deserializeLootTable(tag)) {
            Inventories.fromTag(tag, inventory)
        }
        position = tag?.getDouble("Position") ?: position
    }

    override fun toTag(tag: CompoundTag?): CompoundTag? {
        super.toTag(tag)
        if (!serializeLootTable(tag)) {
            Inventories.toTag(tag, inventory)
        }
        tag?.putDouble("Position", position)
        return tag
    }

    override fun fromClientTag(tag: CompoundTag?) {
        inventory = DefaultedList.ofSize(size(), ItemStack.EMPTY)
        if (!deserializeLootTable(tag)) {
            Inventories.fromTag(tag, inventory)
        }
        position = tag?.getDouble("Position") ?: position
    }

    override fun toClientTag(tag: CompoundTag): CompoundTag {
        if (!serializeLootTable(tag)) {
            Inventories.toTag(tag, inventory)
        }
        tag.putDouble("Position", position)
        return tag
    }

    override fun writeScreenOpeningData(player: ServerPlayerEntity, buf: PacketByteBuf) {
        buf.writeBlockPos(pos)
    }

    fun getSpeedMultiplier(): Double {
        val item = inventory[0].item
        return if (position > 0) 0.0 else when (item) {
            IRItemRegistry.STONE_DRILL_HEAD -> 0.5
            IRItemRegistry.IRON_DRILL_HEAD -> 2.0
            IRItemRegistry.DIAMOND_DRILL_HEAD -> 5.0
            IRItemRegistry.NETHERITE_DRILL_HEAD -> 10.0
            else -> 0.0
        }
    }

    companion object {
        fun isValidDrill(item: Item) =
            item == IRItemRegistry.STONE_DRILL_HEAD
                    || item == IRItemRegistry.IRON_DRILL_HEAD
                    || item == IRItemRegistry.DIAMOND_DRILL_HEAD
                    || item == IRItemRegistry.NETHERITE_DRILL_HEAD
    }
}