package me.steven.indrev.energy

import dev.technici4n.fasttransferlib.api.Simulation
import dev.technici4n.fasttransferlib.api.energy.EnergyIo
import dev.technici4n.fasttransferlib.api.energy.EnergyMovement
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import me.steven.indrev.IndustrialRevolution
import me.steven.indrev.api.machines.Tier
import me.steven.indrev.blocks.machine.CableBlock
import me.steven.indrev.utils.energyOf
import me.steven.indrev.utils.isLoaded
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.StringTag
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.chunk.Chunk
import java.util.*
import kotlin.collections.ArrayDeque

class EnergyNetwork(
    val world: ServerWorld,
    val cables: MutableSet<BlockPos> = hashSetOf(),
    val machines: MutableMap<BlockPos, EnumSet<Direction>> = hashMapOf()
) {
    var lastSenderSize = 0
    var lastReceiverSize = 0

    var tier = Tier.MK1
    private val maxCableTransfer: Double
        get() = when (tier) {
            Tier.MK1 -> IndustrialRevolution.CONFIG.cables.cableMk1.maxInput
            Tier.MK2 -> IndustrialRevolution.CONFIG.cables.cableMk2.maxInput
            Tier.MK3 -> IndustrialRevolution.CONFIG.cables.cableMk3.maxInput
            else -> IndustrialRevolution.CONFIG.cables.cableMk4.maxInput
        }

    fun tick(world: ServerWorld) {
        if (machines.isEmpty()) return
        val receiversHandlers = ArrayDeque<EnergyIo>(lastReceiverSize)
        val senderHandlers = ArrayDeque<EnergyIo>(lastSenderSize)
        machines.forEach { (pos, directions) ->
            if (!world.isLoaded(pos)) return@forEach
            directions.forEach inner@{ dir ->
                val energyIo = energyOf(world, pos, dir) ?: return@inner
                if (energyIo.supportsInsertion() && energyIo.maxInput > 0)
                    receiversHandlers.add(energyIo)
                if (energyIo.supportsExtraction() && energyIo.maxOutput > 0)
                    senderHandlers.add(energyIo)
            }
        }

        lastReceiverSize = receiversHandlers.size
        lastSenderSize = senderHandlers.size

        if (senderHandlers.isEmpty() || receiversHandlers.isEmpty()) return

        val totalInput = receiversHandlers.sumByDouble { handler ->
            if (!senderHandlers.contains(handler)) handler.maxInput else 0.0
        }
        val totalEnergy = senderHandlers.sumByDouble { handler -> handler.maxOutput }

        var sender = senderHandlers.removeFirst()
        var receiver = receiversHandlers.removeFirst()
        var sentThisTick = 0.0
        var receivedThisTick = 0.0

        val remainingInputs = Object2DoubleOpenHashMap<EnergyIo>()
        remainingInputs.defaultReturnValue(0.0)

        while (true) {
            val maxInput = receiver.maxInput

            val amount = ((maxInput / totalInput) * totalEnergy).coerceIn(1.0, maxCableTransfer).coerceAtMost(maxInput - receivedThisTick)

            val energyBefore = receiver.energy
            val moved = EnergyMovement.move(sender, receiver, amount)
            val energyAfter = receiver.energy

            val isSame = moved > 0 && energyBefore == energyAfter
            if (isSame) {
                if (senderHandlers.isNotEmpty()) {
                    sender = senderHandlers.removeFirst()
                    sentThisTick = 0.0
                }
                else if (receiversHandlers.isNotEmpty()) {
                    receiver = receiversHandlers.removeFirst()
                    receivedThisTick = remainingInputs.getDouble(receiver)
                }
                else break
                continue
            }
            sentThisTick += moved
            receivedThisTick += moved

            if (sentThisTick >= sender.maxOutput || sentThisTick.isNaN()) {
                if (senderHandlers.isEmpty()) break
                sender = senderHandlers.removeFirst()
                sentThisTick = 0.0
            }
            if (receivedThisTick >= receiver.maxInput || receivedThisTick >= amount || receivedThisTick.isNaN()) {
                if (!isSame && receivedThisTick < receiver.maxInput) {
                    remainingInputs.addTo(receiver, receivedThisTick)
                    receiversHandlers.addLast(receiver)
                }
                if (receiversHandlers.isEmpty()) break
                receiver = receiversHandlers.removeFirst()
                receivedThisTick = remainingInputs.getDouble(receiver)
            }
        }
    }

    fun remove() {
        val state = EnergyNetworkState.getNetworkState(world)
        state.networks.remove(this)
        cables.forEach { state.networksByPos.remove(it) }
    }

    fun appendCable(state: EnergyNetworkState, block: CableBlock, blockPos: BlockPos) {
        tier = block.tier
        cables.add(blockPos)
        state.networksByPos[blockPos] = this
    }

    fun appendMachine(blockPos: BlockPos, direction: Direction) {
        machines.computeIfAbsent(blockPos) { EnumSet.noneOf(Direction::class.java) }.add(direction)
    }

    fun toTag(tag: CompoundTag) {
        val cablesList = ListTag()
        cables.forEach { pos ->
            cablesList.add(LongTag.of(pos.asLong()))
        }
        val machinesList = ListTag()
        machines.forEach { (pos, directions) ->
            val machineTag = CompoundTag()
            machineTag.putLong("pos", pos.asLong())
            val dirList = ListTag()
            directions.forEach { dir ->
                dirList.add(StringTag.of(dir.toString()))
            }
            machineTag.put("dir", dirList)
            machinesList.add(machineTag)
        }
        tag.put("cables", cablesList)
        tag.put("machines", machinesList)
        tag.putInt("tier", tier.ordinal)
    }

    companion object {

        private const val MAX_VALUE = (Integer.MAX_VALUE - 1).toDouble()

        private val EnergyIo.maxInput: Double
            get() = MAX_VALUE - insert(MAX_VALUE, Simulation.SIMULATE)
        private val EnergyIo.maxOutput: Double
            get() = extract(MAX_VALUE, Simulation.SIMULATE)

        fun handleBreak(world: ServerWorld, pos: BlockPos) {
            val state = EnergyNetworkState.getNetworkState(world)
            if (state.networksByPos.containsKey(pos))
                state.networksByPos[pos]?.remove()
            Direction.values().forEach {
                val offset = pos.offset(it)
                handleUpdate(world, offset)
            }
        }

        fun handleUpdate(world: ServerWorld, pos: BlockPos) {
            val state = EnergyNetworkState.getNetworkState(world)
            if (state.networksByPos.containsKey(pos))
                state.networksByPos[pos]?.remove()
            val network = EnergyNetwork(world)
            state.networks.add(network)
            val scanned = hashSetOf<BlockPos>()
            Direction.values().forEach { dir ->
                buildNetwork(scanned, state, network, world.getChunk(pos), world, pos, pos, dir)
            }
            if (network.machines.isEmpty() || network.cables.isEmpty())
                network.remove()
            state.markDirty()
        }

        private fun buildNetwork(scanned: MutableSet<BlockPos>, state: EnergyNetworkState, network: EnergyNetwork, chunk: Chunk, world: ServerWorld, blockPos: BlockPos, source: BlockPos, direction: Direction) {
            if (energyOf(world, blockPos, direction.opposite) != null) {
                network.appendMachine(blockPos, direction.opposite)
            }
            if (blockPos != source && !scanned.add(blockPos)) return
            val blockState = chunk.getBlockState(blockPos) ?: return
            val block = blockState.block
            if (block is CableBlock) {
                if (state.networksByPos.containsKey(blockPos)) {
                    val oldNetwork = state.networksByPos[blockPos]
                    if (state.networks.contains(oldNetwork) && oldNetwork != network) {
                        oldNetwork?.remove()
                    }
                }
                Direction.values().forEach { dir ->
                    if (blockState[CableBlock.getProperty(dir)]) {
                        val nPos = blockPos.offset(dir)
                        if (nPos.x shr 4 == chunk.pos.x && nPos.z shr 4 == chunk.pos.z)
                            buildNetwork(scanned, state, network, chunk, world, nPos, source, dir)
                        else
                            buildNetwork(scanned, state, network, world.getChunk(nPos), world, nPos, source, dir)
                    }
                }
                if (blockState[CableBlock.getProperty(direction.opposite)])
                    network.appendCable(state, block, blockPos.toImmutable())
            }
        }

        fun fromTag(world: ServerWorld, tag: CompoundTag): EnergyNetwork {
            val cablesList = tag.getList("cables", 4)
            val machinesList = tag.getList("machines", 10)
            val network = EnergyNetwork(world)
            cablesList.forEach { cableTag ->
                cableTag as LongTag
                network.cables.add(BlockPos.fromLong(cableTag.long).toImmutable())
            }
            machinesList.forEach { machineTag ->
                machineTag as CompoundTag
                val posLong = machineTag.getLong("pos")
                val pos = BlockPos.fromLong(posLong)
                val dirList = machineTag.getList("dir", 8)
                val directions = EnumSet.noneOf(Direction::class.java)
                dirList.forEach { dirTag ->
                    dirTag as StringTag
                    val dir = Direction.valueOf(dirTag.asString().toUpperCase())
                    directions.add(dir)
                }
                network.machines[pos] = directions
            }
            val tier = Tier.values()[tag.getInt("tier")]
            network.tier = tier
            return network
        }
    }
}