package ru.lumine.feed;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

/** Administrative food commands from the live 1.1.0 mod, with explicit console permissions. */
public final class AdminFoodCommands {
    private AdminFoodCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : List.of("feed", "heal")) {
            LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(name)
                    .requires(AdminFoodCommands::allowed)
                    .executes(context -> execute(context.getSource(), name, null))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(context.getSource(), builder))
                            .executes(context -> execute(context.getSource(), name,
                                    StringArgumentType.getString(context, "player"))));
            replace(dispatcher, command);
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
            CommandSourceStack source, SuggestionsBuilder builder) {
        if (allowed(source)) {
            source.getServer().getPlayerList().getPlayers().forEach(player ->
                    builder.suggest(player.getGameProfile().name()));
        }
        return builder.buildFuture();
    }

    static <S> void replace(CommandDispatcher<S> dispatcher, LiteralArgumentBuilder<S> builder) {
        CommandNode<S> root = dispatcher.getRoot();
        if (root.getChild(builder.getLiteral()) == null) {
            dispatcher.register(builder);
            return;
        }
        try {
            List<Field> fields = List.of(
                    CommandNode.class.getDeclaredField("children"),
                    CommandNode.class.getDeclaredField("literals"),
                    CommandNode.class.getDeclaredField("arguments"));
            for (Field field : fields) field.setAccessible(true);
            for (Field field : fields) {
                @SuppressWarnings("unchecked")
                Map<String, CommandNode<S>> nodes = (Map<String, CommandNode<S>>) field.get(root);
                nodes.remove(builder.getLiteral());
            }
            dispatcher.register(builder);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot replace admin food commands safely", error);
        }
    }

    public static boolean allowed(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.getEntity() == null && source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        }
        if (!source.getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) {
            return false;
        }
        return gateAllows(player);
    }

    /** Standalone servers use vanilla OP checks; an installed D2Gate must also unlock the player. */
    static boolean gateAllows(ServerPlayer player) {
        try {
            Class<?> type = Class.forName("ru.d2omg.d2gate.D2GateMod");
            Field instanceField = type.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Object gate = instanceField.get(null);
            if (gate == null) return false;
            Method isLocked = type.getDeclaredMethod("isLocked", ServerPlayer.class);
            isLocked.setAccessible(true);
            return Boolean.FALSE.equals(isLocked.invoke(gate, player));
        } catch (ClassNotFoundException absentOptionalIntegration) {
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            return false;
        }
    }

    private static int execute(CommandSourceStack source, String name, String targetName) {
        if (!allowed(source)) return 0;
        ServerPlayer target = targetName == null ? source.getPlayer()
                : source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal(targetName == null
                    ? "Из консоли укажи ник игрока." : "Игрок не в сети на этом сервере."));
            return 0;
        }
        if (!target.isAlive()) {
            source.sendFailure(Component.literal("Игрок должен быть жив."));
            return 0;
        }
        target.getFoodData().setFoodLevel(20);
        target.getFoodData().setSaturation(20.0F);
        if (name.equals("heal")) {
            target.setHealth(target.getMaxHealth());
            target.removeAllEffects();
            target.setRemainingFireTicks(0);
        }
        String message = (name.equals("heal") ? "Здоровье и голод восстановлены: "
                : "Голод восстановлен: ") + target.getGameProfile().name();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
