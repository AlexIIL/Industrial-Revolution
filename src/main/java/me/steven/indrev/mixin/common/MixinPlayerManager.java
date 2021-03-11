package me.steven.indrev.mixin.common;

import me.steven.indrev.api.IRServerPlayerEntityExtension;
import me.steven.indrev.registry.PacketRegistry;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {
    @Inject(method = "onPlayerConnect", at = @At("RETURN"))
    private void indrev_onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        PacketRegistry.INSTANCE.syncVeinData(player);
        PacketRegistry.INSTANCE.syncConfig(player);
        if (player instanceof IRServerPlayerEntityExtension) {
            ((IRServerPlayerEntityExtension) player).sync();
        }
    }
}
