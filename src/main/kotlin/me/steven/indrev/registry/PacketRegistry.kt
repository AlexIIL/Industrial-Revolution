package me.steven.indrev.registry

import alexiil.mc.lib.attributes.fluid.FluidInvUtil
import io.netty.buffer.Unpooled
import me.steven.indrev.IndustrialRevolution
import me.steven.indrev.IndustrialRevolutionClient
import me.steven.indrev.api.IRPlayerEntityExtension
import me.steven.indrev.api.machines.TransferMode
import me.steven.indrev.api.sideconfigs.Configurable
import me.steven.indrev.api.sideconfigs.ConfigurationType
import me.steven.indrev.blockentities.GlobalStateController
import me.steven.indrev.blockentities.MachineBlockEntity
import me.steven.indrev.blockentities.crafters.CraftingMachineBlockEntity
import me.steven.indrev.blockentities.farms.AOEMachineBlockEntity
import me.steven.indrev.blockentities.farms.MiningRigBlockEntity
import me.steven.indrev.blockentities.farms.RancherBlockEntity
import me.steven.indrev.blockentities.modularworkbench.ModularWorkbenchBlockEntity
import me.steven.indrev.config.IRConfig
import me.steven.indrev.config.IRConfig.writeToClient
import me.steven.indrev.gui.screenhandlers.IRGuiScreenHandler
import me.steven.indrev.gui.screenhandlers.machines.ModularWorkbenchScreenHandler
import me.steven.indrev.gui.screenhandlers.machines.RancherScreenHandler
import me.steven.indrev.gui.screenhandlers.pipes.PipeFilterScreen
import me.steven.indrev.gui.screenhandlers.pipes.PipeFilterScreenHandler
import me.steven.indrev.gui.widgets.machines.WFluid
import me.steven.indrev.networks.EndpointData
import me.steven.indrev.networks.Network
import me.steven.indrev.networks.client.ClientNetworkState
import me.steven.indrev.networks.item.ItemNetworkState
import me.steven.indrev.recipes.machines.ModuleRecipe
import me.steven.indrev.tools.modular.ArmorModule
import me.steven.indrev.utils.SPLIT_STACKS_PACKET
import me.steven.indrev.utils.entries
import me.steven.indrev.utils.getAllOfType
import me.steven.indrev.utils.isLoaded
import me.steven.indrev.world.chunkveins.VeinType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.collection.WeightedList
import net.minecraft.util.math.Direction
import net.minecraft.util.registry.Registry
import kotlin.collections.MutableMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.hashMapOf
import kotlin.collections.set

object PacketRegistry {
    fun registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(Configurable.UPDATE_MACHINE_SIDE_PACKET_ID) { server, player, _, buf, _ ->
            val type = buf.readEnumConstant(ConfigurationType::class.java)
            val pos = buf.readBlockPos()
            val dir = Direction.byId(buf.readInt())
            val mode = TransferMode.values()[buf.readInt()]
            server.execute {
                val world = player.world
                val blockEntity = world.getBlockEntity(pos) as? MachineBlockEntity<*> ?: return@execute
                blockEntity.getCurrentConfiguration(type)[dir] = mode
                blockEntity.markDirty()
                blockEntity.sync()
                GlobalStateController.update(world, pos, false)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(Configurable.UPDATE_AUTO_OPERATION_PACKET_ID) { server, player, _, buf, _ ->
            val type = buf.readEnumConstant(ConfigurationType::class.java)
            val opType = buf.readByte()
            val pos = buf.readBlockPos()
            val value = buf.readBoolean()
            server.execute {
                val world = player.world
                val blockEntity = world.getBlockEntity(pos) as? MachineBlockEntity<*> ?: return@execute
                if (opType.toInt() == 0)
                    blockEntity.getCurrentConfiguration(type).autoPush = value
                else
                    blockEntity.getCurrentConfiguration(type).autoPull = value
                blockEntity.markDirty()
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AOEMachineBlockEntity.UPDATE_VALUE_PACKET_ID) { server, player, _, buf, _ ->
            val value = buf.readInt()
            val pos = buf.readBlockPos()
            val world = player.world
            server.execute {
                if (world.isLoaded(pos)) {
                    val blockEntity = world.getBlockEntity(pos) as? AOEMachineBlockEntity<*> ?: return@execute
                    blockEntity.range = value
                    blockEntity.markDirty()
                    blockEntity.sync()
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(WFluid.FLUID_CLICK_PACKET) { server, player, _, buf, _ ->
            val pos = buf.readBlockPos()
            val tank = buf.readInt()
            val world = player.world
            server.execute {
                if (world.isLoaded(pos)) {
                    val blockEntity = world.getBlockEntity(pos) as? MachineBlockEntity<*> ?: return@execute
                    val fluidComponent = blockEntity.fluidComponent ?: return@execute
                    FluidInvUtil.interactCursorWithTank(
                        fluidComponent.getInteractInventory(tank),
                        player,
                        fluidComponent.getFilterForTank(tank)
                    )
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(IndustrialRevolution.UPDATE_MODULAR_TOOL_LEVEL) { server, player, _, buf, _ ->
            val key = buf.readString(32767)
            val value = buf.readInt()
            val slot = buf.readInt()
            server.execute {
                val stack = player.inventory.getStack(slot)
                if (!stack.isEmpty) {
                    val tag = stack.getOrCreateSubNbt("selected")
                    tag.putInt(key, value)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(SPLIT_STACKS_PACKET) { server, player, _, buf, _ ->
            val pos = buf.readBlockPos()
            server.execute {
                val world = player.world
                if (world.isLoaded(pos)) {
                    val blockEntity = world.getBlockEntity(pos) as? CraftingMachineBlockEntity<*> ?: return@execute
                    blockEntity.isSplitOn = !blockEntity.isSplitOn
                    if (blockEntity.isSplitOn) blockEntity.splitStacks()
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RancherScreenHandler.SYNC_RANCHER_CONFIG) { server, player, _, buf, _ ->
            val pos = buf.readBlockPos()
            val feedBabies = buf.readBoolean()
            val mateAdults = buf.readBoolean()
            val matingLimit = buf.readInt()
            val killAfter = buf.readInt()
            server.execute {
                val world = player.world
                if (world.isLoaded(pos)) {
                    val blockEntity = world.getBlockEntity(pos) as? RancherBlockEntity ?: return@execute
                    blockEntity.feedBabies = feedBabies
                    blockEntity.mateAdults = mateAdults
                    blockEntity.matingLimit = matingLimit
                    blockEntity.killAfter = killAfter
                    blockEntity.markDirty()
                    blockEntity.sync()
                }
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(ModularWorkbenchScreenHandler.MODULE_SELECT_PACKET) { server, player, _, buf, _ ->
            val syncId = buf.readInt()
            val recipeId = buf.readIdentifier()
            val pos = buf.readBlockPos()
            val screenHandler =
                player.currentScreenHandler as? ModularWorkbenchScreenHandler ?: return@registerGlobalReceiver
            if (syncId != screenHandler.syncId) return@registerGlobalReceiver
            server.execute {
                val world = player.world
                if (world.isLoaded(pos)) {
                    val recipe = server.recipeManager.getAllOfType(ModuleRecipe.TYPE)[recipeId]!!
                    screenHandler.layoutSlots(recipe)
                    val blockEntity = world.getBlockEntity(pos) as? ModularWorkbenchBlockEntity ?: return@execute
                    blockEntity.selectedRecipe = recipeId
                    blockEntity.markDirty()
                    blockEntity.sync()
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PipeFilterScreenHandler.CLICK_FILTER_SLOT_PACKET) { server, player, _, buf, _ ->
            val slotIndex = buf.readInt()
            val dir = buf.readEnumConstant(Direction::class.java)
            val pos = buf.readBlockPos()
            server.execute {
                val cursorStack = player.currentScreenHandler.cursorStack
                val state = Network.Type.ITEM.getNetworkState(player.serverWorld) as? ItemNetworkState ?: return@execute
                val data = state.getFilterData(pos, dir)
                if (cursorStack.isEmpty) data.filter[slotIndex] = ItemStack.EMPTY
                else data.filter[slotIndex] = cursorStack.copy().also { it.count = 1 }
                state.markDirty()
                val syncPacket = PacketByteBufs.create()
                syncPacket.writeInt(slotIndex)
                syncPacket.writeItemStack(data.filter[slotIndex])
                ServerPlayNetworking.send(player, PipeFilterScreenHandler.UPDATE_FILTER_SLOT_S2C_PACKET, syncPacket)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PipeFilterScreenHandler.CHANGE_FILTER_MODE_PACKET) { server, player, _, buf, _ ->
            val dir = buf.readEnumConstant(Direction::class.java)
            val pos = buf.readBlockPos()
            val field = buf.readInt()
            val value = buf.readBoolean()

            server.execute {
                val state = Network.Type.ITEM.getNetworkState(player.serverWorld) as? ItemNetworkState ?: return@execute
                val data = state.getFilterData(pos, dir, true)
                when (field) {
                    0 -> data.whitelist = value
                    1 -> data.matchDurability = value
                    2 -> data.matchTag = value
                    else -> return@execute
                }
                state.markDirty()
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PipeFilterScreenHandler.CHANGE_SERVO_MODE_PACKET) { server, player, _, buf, _ ->
            val dir = buf.readEnumConstant(Direction::class.java)
            val pos = buf.readBlockPos()
            val mode = buf.readEnumConstant(EndpointData.Mode::class.java)

            server.execute {
                val state = Network.Type.ITEM.getNetworkState(player.serverWorld) as? ItemNetworkState ?: return@execute
                val data = state.getEndpointData(pos, dir, true) ?: return@execute
                data.mode = mode
                state.markDirty()
            }
        }
    }

    fun syncVeinData(playerEntity: ServerPlayerEntity) {
        val buf = PacketByteBuf(Unpooled.buffer())
        buf.writeInt(VeinType.REGISTERED.size)
        VeinType.REGISTERED.forEach { (identifier, veinType) ->
            buf.writeIdentifier(identifier)
            val entries = veinType.outputs.entries
            val infiniteEntries = veinType.infiniteOutputs.entries
            buf.writeInt(entries.size)

            for (i in 0 until entries.size) {
                val entry = entries[i]
                val block = entry.element
                val weight = entry.weight
                val infiniteWeight = infiniteEntries[i].weight
                val rawId = Registry.BLOCK.getRawId(block)
                buf.writeInt(rawId)
                buf.writeInt(weight)
                buf.writeInt(infiniteWeight)
            }
            buf.writeInt(veinType.sizeRange.first)
            buf.writeInt(veinType.sizeRange.last)
        }
        ServerPlayNetworking.send(playerEntity, IndustrialRevolution.SYNC_VEINS_PACKET, buf)
    }

    fun syncConfig(playerEntity: ServerPlayerEntity) {
        val buf = PacketByteBufs.create()
        writeToClient(buf)
        ServerPlayNetworking.send(playerEntity, IndustrialRevolution.SYNC_CONFIG_PACKET, buf)
    }

    fun registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(IndustrialRevolution.SYNC_VEINS_PACKET) { _, _, buf, _ ->
            val totalVeins = buf.readInt()
            for (x in 0 until totalVeins) {
                val id = buf.readIdentifier()
                val entriesSize = buf.readInt()
                val outputs = WeightedList<Block>()
                val infiniteOutputs = WeightedList<Block>()
                for (y in 0 until entriesSize) {
                    val rawId = buf.readInt()
                    val weight = buf.readInt()
                    val infiniteWeight = buf.readInt()
                    val block = Registry.BLOCK.get(rawId)
                    outputs.add(block, weight)
                    infiniteOutputs.add(block, infiniteWeight)
                }
                val minSize = buf.readInt()
                val maxSize = buf.readInt()
                val veinType = VeinType(id, outputs, infiniteOutputs, minSize..maxSize)
                VeinType.REGISTERED[id] = veinType
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(IndustrialRevolution.SYNC_PROPERTY) { client, _, buf, _ ->
            val syncId = buf.readInt()
            val property = buf.readInt()
            val value = buf.readInt()
            client.execute {
                val handler = client.player!!.currentScreenHandler
                if (handler.syncId == syncId)
                    (handler as? IRGuiScreenHandler)?.propertyDelegate?.set(property, value)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(MiningRigBlockEntity.BLOCK_BREAK_PACKET) { client, _, buf, _ ->
            val pos = buf.readBlockPos().down()
            val blockRawId = buf.readInt()
            val block = Registry.BLOCK.get(blockRawId)
            client.execute {
                MinecraftClient.getInstance().particleManager.addBlockBreakParticles(pos, block.defaultState)
                val blockSoundGroup = block.getSoundGroup(block.defaultState)
                (client.player!!.world as ClientWorld).playSound(
                    pos,
                    blockSoundGroup.breakSound,
                    SoundCategory.BLOCKS,
                    (blockSoundGroup.getVolume() + 1.0f) / 4.0f,
                    blockSoundGroup.getPitch() * 0.8f,
                    false
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(IndustrialRevolution.SYNC_MODULE_PACKET) { client, _, buf, _ ->
            val size = buf.readInt()
            val modules = hashMapOf<ArmorModule, Int>()
            for (index in 0 until size) {
                val ordinal = buf.readInt()
                val module = ArmorModule.values()[ordinal]
                val level = buf.readInt()
                modules[module] = level
            }
            val durability = buf.readDouble()
            val isRegenerating = buf.readBoolean()
            client.execute {
                val player = client.player!!
                if (player is IRPlayerEntityExtension) {
                    (player.getAppliedModules() as MutableMap<*, *>).clear()
                    modules.forEach(player::applyModule)
                    player.shieldDurability = durability
                    player.isRegenerating = isRegenerating
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(GlobalStateController.UPDATE_PACKET_ID) { client, _, buf, _ ->
            val pos = buf.readBlockPos()
            val workingState = buf.readBoolean()
            client.execute {
                GlobalStateController.workingStateTracker[pos.asLong()] = workingState
                GlobalStateController.queueUpdate(pos)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PipeFilterScreenHandler.UPDATE_FILTER_SLOT_S2C_PACKET) { client, _, buf, _ ->
            val slotIndex = buf.readInt()
            val stack = buf.readItemStack()
            client.execute {
                val screen = client.currentScreen as? PipeFilterScreen ?: return@execute
                val controller = screen.screenHandler
                controller.backingList[slotIndex] = stack
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(IndustrialRevolution.SYNC_CONFIG_PACKET) { client, _, buf, _ ->
            IRConfig.readFromServer(buf)
        }

        ClientPlayNetworking.registerGlobalReceiver(IndustrialRevolution.SYNC_NETWORK_SERVOS) { client, _, buf, _ ->
            val type = Network.Type.valueOf(buf.readString())
            val state = IndustrialRevolutionClient.CLIENT_NETWORK_STATE.computeIfAbsent(type) { ClientNetworkState(type) }
            state.processPacket(buf, client)
        }
    }
}