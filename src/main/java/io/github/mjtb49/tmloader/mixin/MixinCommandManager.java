package io.github.mjtb49.tmloader.mixin;

import com.mojang.brigadier.CommandDispatcher;
import io.github.mjtb49.tmloader.command.TMCommand;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandManager.class)
public abstract class MixinCommandManager {
    @Shadow @Final private CommandDispatcher<ServerCommandSource> dispatcher;


    @Inject(method = "<init>(Lnet/minecraft/server/command/CommandManager$RegistrationEnvironment;)V", at = @At("RETURN"))
    public void CommandManager(CommandManager.RegistrationEnvironment environment, CallbackInfo ci) {
        TMCommand.register(dispatcher);
    }
}
