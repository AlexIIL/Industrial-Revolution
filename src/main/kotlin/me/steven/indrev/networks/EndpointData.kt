package me.steven.indrev.networks

import alexiil.mc.lib.attributes.fluid.amount.FluidAmount
import alexiil.mc.lib.attributes.fluid.filter.FluidFilter
import alexiil.mc.lib.attributes.item.filter.ItemFilter
import me.steven.indrev.utils.groupedFluidInv
import me.steven.indrev.utils.groupedItemInv
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import java.util.*
import kotlin.random.Random

data class EndpointData(var type: Type, var mode: Mode?) {

    fun writeNbt(tag: NbtCompound): NbtCompound {
        tag.putInt("t", type.ordinal)
        if (mode != null)
            tag.putInt("m", mode!!.ordinal)
        return tag
    }

    fun readNbt(tag: NbtCompound): EndpointData {
        val type = Type.VALUES[tag.getInt("t")]
        val mode = if (tag.contains("m")) Mode.VALUES[tag.getInt("m")] else null
        return EndpointData(type, mode)
    }

    enum class Type {
        RETRIEVER,
        OUTPUT,
        INPUT;

        companion object {
            val VALUES = values()
        }
    }

    enum class Mode {
        ROUND_ROBIN {
            override fun getFluidSorter(world: World, type: Type, filter: FluidFilter): (Array<Any?>) -> Unit {
                return { array ->
                    Arrays.sort(array,
                        if (type == Type.RETRIEVER)
                            Comparator.comparing<Any?, FluidAmount> { node ->
                                node as Node
                                groupedFluidInv(world, node.target, node.direction).getAmount_F(filter)
                            }.reversed()
                        else
                            Comparator.comparing {
                                it as Node
                                groupedFluidInv(world, it.target, it.direction).getAmount_F(filter)
                            })
                }
            }

            override fun getItemSorter(world: World, type: Type, filter: ItemFilter): (Array<Any?>) -> Unit {
                return { array ->
                    Arrays.sort(array,
                        (if (type == Type.RETRIEVER)
                            Comparator.comparing<Any, Int> { node ->
                                node as Node
                                groupedItemInv(world, node.target, node.direction).getAmount(filter)
                            }.reversed()
                        else
                            Comparator.comparing {
                                it as Node
                                groupedItemInv(world, it.target, it.direction).getAmount(filter)
                            })
                    )
                }
            }
        },
        FURTHEST_FIRST {
            override fun getFluidSorter(world: World, type: Type, filter: FluidFilter): (Array<Any?>) -> Unit {
                return { array ->
                    Arrays.sort(array) { first, second ->
                        first as Node
                        second as Node
                        when {
                            first.dist > second.dist -> -1
                            first.dist < second.dist -> 1
                            else -> 0
                        }
                    }
                }
            }

            override fun getItemSorter(world: World, type: Type, filter: ItemFilter): (Array<Any?>) -> Unit {
                return { array ->
                    Arrays.sort(array) { first, second ->
                        first as Node
                        second as Node
                        when {
                            first.dist > second.dist -> -1
                            first.dist < second.dist -> 1
                            else -> 0
                        }
                    }
                }
            }
        },
        NEAREST_FIRST {
            override fun getFluidSorter(world: World, type: Type, filter: FluidFilter): (Array<Any?>) -> Unit {
                return { array -> Arrays.sort(array) }
            }

            override fun getItemSorter(world: World, type: Type, filter: ItemFilter): (Array<Any?>) -> Unit {
                return { array -> Arrays.sort(array) }
            }
        },
        RANDOM {
            override fun getFluidSorter(world: World, type: Type, filter: FluidFilter): (Array<Any?>) -> Unit {
                return { array -> array.shuffle() }
            }

            override fun getItemSorter(world: World, type: Type, filter: ItemFilter): (Array<Any?>) -> Unit {
                return { array -> array.shuffle() }
            }
        };

        abstract fun getFluidSorter(world: World, type: Type, filter: FluidFilter): (Array<Any?>) -> Unit

        abstract fun getItemSorter(world: World, type: Type, filter: ItemFilter): (Array<Any?>) -> Unit

        fun next(): Mode {
            return when (this) {
                ROUND_ROBIN -> FURTHEST_FIRST
                FURTHEST_FIRST -> NEAREST_FIRST
                NEAREST_FIRST -> RANDOM
                RANDOM -> ROUND_ROBIN
            }
        }

        companion object {
            val R = Random(System.currentTimeMillis())
            val VALUES = values()
        }
    }
}