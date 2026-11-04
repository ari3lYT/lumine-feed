package ru.lumine.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AdminFoodCommandsTest {
    @Test
    void vanillaServersDoNotRequireTheOptionalD2GateMod() {
        assertTrue(AdminFoodCommands.gateAllows(null));
    }

    @Test
    void replacesExistingCommandRoot() throws Exception {
        CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<String>literal("feed").executes(context -> 1));
        AdminFoodCommands.replace(dispatcher,
                LiteralArgumentBuilder.<String>literal("feed").executes(context -> 2));
        assertEquals(2, dispatcher.execute("feed", "console"));
    }

    @Test
    void onlyOwnerConsoleCanUseAdministrativeFoodCommands() {
        assertFalse(AdminFoodCommands.allowed(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(AdminFoodCommands.allowed(source(PermissionSet.ALL_PERMISSIONS)));
    }

    private static CommandSourceStack source(PermissionSet permissions) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                null, permissions, Component.literal("console"), null);
    }
}
