package ru.lumine.feed;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LumineFeed implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("lumine-feed");
    private static final Path PREFS = Path.of("config/lumine-feed/player-prefs.properties");
    private static final Path MESSAGES = Path.of("config/lumine-feed/messages.properties");

    @Override
    public void onInitialize() {
        ensureMessages();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()
                    || !(player instanceof ServerPlayer feeder)
                    || !(entity instanceof ServerPlayer target)
                    || feeder == target
                    || feeder.isSpectator()
                    || target.isSpectator()
                    || !target.isAlive()) {
                return InteractionResult.PASS;
            }

            ItemStack held = feeder.getItemInHand(hand);
            FoodProperties food = held.get(DataComponents.FOOD);
            Consumable consumable = held.get(DataComponents.CONSUMABLE);
            if (held.isEmpty() || food == null || consumable == null) {
                return InteractionResult.PASS;
            }

            var originalItem = held.getItem();
            String foodName = held.getHoverName().getString();
            ItemStack single = held.copyWithCount(1);
            if (!consumable.canConsume(target, single)) {
                target.sendSystemMessage(Component.literal(msg("target.full", "Вы не голодны."))
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS_SERVER;
            }

            boolean effectFood = hasSpecialEffects(held, consumable);
            boolean effectAllowed;
            try {
                effectAllowed = !effectFood || allowsEffectFood(target.getUUID());
            } catch (UncheckedIOException error) {
                LOGGER.error("Cannot read feed consent; refusing effect food", error);
                feeder.sendSystemMessage(Component.literal("Не удалось прочитать настройки кормления. Попробуй позже.")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!effectAllowed) {
                feeder.sendSystemMessage(Component.literal(format(
                        msg("effect.denied.feeder", "{target} запретил кормить себя едой с эффектами."),
                        "{target}", target.getGameProfile().name()))
                        .withStyle(ChatFormatting.YELLOW), true);
                target.sendSystemMessage(Component.literal(msg("effect.denied.target",
                        "Еду с эффектами вам не дали: у вас это отключено. /feedtoggle on"))
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS_SERVER;
            }

            ItemStack remainder = single.finishUsingItem(world, target);
            if (!feeder.isCreative()) {
                held.shrink(1);
                if (!remainder.isEmpty() && remainder.getItem() != originalItem) {
                    feeder.getInventory().placeItemBackInInventory(remainder, Prediction.SERVER_ONLY);
                }
            }
            feeder.swing(hand, SwingAnimation.DEFAULT, true);
            feeder.sendSystemMessage(Component.literal(format(
                    msg("fed.feeder", "Вы покормили {target}: {food}"),
                    "{target}", target.getGameProfile().name(),
                    "{food}", foodName))
                    .withStyle(ChatFormatting.GREEN), true);
            target.sendSystemMessage(Component.literal(format(
                    msg("fed.target", "{feeder} покормил вас: {food}"),
                    "{feeder}", feeder.getGameProfile().name(),
                    "{food}", foodName))
                    .withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS_SERVER;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
            AdminFoodCommands.register(dispatcher);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            AdminFoodCommands.register(server.getCommands().getDispatcher());
            migrateMessages();
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("feedtoggle")
                .executes(ctx -> toggleEffects(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("effects")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> setEffects(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setEffects(ctx.getSource(), false)))
                        .then(Commands.literal("toggle").executes(ctx -> toggleEffects(ctx.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        final boolean enabled;
        try {
            enabled = allowsEffectFood(player.getUUID());
        } catch (UncheckedIOException error) {
            LOGGER.error("Cannot read feed consent", error);
            source.sendFailure(Component.literal("Не удалось прочитать настройки кормления."));
            return 0;
        }
        player.sendSystemMessage(Component.literal(format(
                msg("status", "Кормление едой с эффектами: {state}. Обычной едой вас можно кормить всегда."),
                "{state}", enabled ? "§aразрешено" : "§cзапрещено")), false);
        return 1;
    }

    private static int setEffects(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        try {
            saveEffectFood(player.getUUID(), enabled);
        } catch (UncheckedIOException error) {
            LOGGER.error("Cannot save feed consent", error);
            source.sendFailure(Component.literal("Не удалось сохранить настройки кормления."));
            return 0;
        }
        player.sendSystemMessage(Component.literal(enabled
                ? msg("effect.on", "Теперь вас можно кормить едой с эффектами.")
                : msg("effect.off", "Еда с эффектами при кормлении запрещена.")), false);
        return 1;
    }

    private static int toggleEffects(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        try {
            return setEffects(source, !allowsEffectFood(player.getUUID()));
        } catch (UncheckedIOException error) {
            LOGGER.error("Cannot read feed consent", error);
            source.sendFailure(Component.literal("Не удалось прочитать настройки кормления."));
            return 0;
        }
    }

    private static boolean hasSpecialEffects(ItemStack stack, Consumable consumable) {
        if (!consumable.onConsumeEffects().isEmpty()) return true;
        // Special vanilla food behavior not always represented as a status-effect list.
        return stack.is(Items.CHORUS_FRUIT) || stack.is(Items.HONEY_BOTTLE);
    }

    private static synchronized boolean allowsEffectFood(UUID uuid) {
        Properties p = load(PREFS);
        return Boolean.parseBoolean(p.getProperty(uuid + ".allowEffectFood", "false"));
    }

    private static synchronized void saveEffectFood(UUID uuid, boolean enabled) {
        Properties p = load(PREFS);
        p.setProperty(uuid + ".allowEffectFood", Boolean.toString(enabled));
        save(PREFS, p, "Lumine Feed recipient preferences");
    }

    static Properties load(Path path) {
        Properties p = new Properties();
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read feed settings: " + path, error);
        }
        return p;
    }

    static void save(Path path, Properties p, String comment) {
        Path temporary = null;
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent == null) throw new IOException("Feed settings have no parent directory: " + path);
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "feed-settings-", ".tmp");
            try (Writer w = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                p.store(w, comment);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot save feed settings: " + path, error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException error) { LOGGER.warn("Cannot clean up temporary feed settings: {}", temporary, error); }
            }
        }
    }

    private static void migrateMessages() {
        Properties p = load(MESSAGES);
        boolean changed = false;
        for (String key : p.stringPropertyNames()) {
            String old = p.getProperty(key);
            String updated = old.replace("/feed effects on", "/feedtoggle on")
                    .replace("/feed effects off", "/feedtoggle off");
            if (!old.equals(updated)) {
                p.setProperty(key, updated);
                changed = true;
            }
        }
        if (changed) save(MESSAGES, p, "Lumine Feed user-visible messages");
    }

    private static void ensureMessages() {
        if (Files.exists(MESSAGES)) return;
        Properties p = new Properties();
        p.setProperty("target.full", "Вы не голодны.");
        p.setProperty("effect.denied.feeder", "{target} запретил кормить себя едой с эффектами.");
        p.setProperty("effect.denied.target", "Еду с эффектами вам не дали: у вас это отключено. /feedtoggle on");
        p.setProperty("fed.feeder", "Вы покормили {target}: {food}");
        p.setProperty("fed.target", "{feeder} покормил вас: {food}");
        p.setProperty("status", "Кормление едой с эффектами: {state}. Обычной едой вас можно кормить всегда.");
        p.setProperty("effect.on", "Теперь вас можно кормить едой с эффектами.");
        p.setProperty("effect.off", "Еда с эффектами при кормлении запрещена.");
        save(MESSAGES, p, "Lumine Feed user-visible messages");
    }

    private static String msg(String key, String fallback) {
        try { return load(MESSAGES).getProperty(key, fallback); }
        catch (UncheckedIOException error) {
            LOGGER.error("Cannot read feed messages; using built-in text", error);
            return fallback;
        }
    }

    private static String format(String value, String... kv) {
        String out = value;
        for (int i = 0; i + 1 < kv.length; i += 2) out = out.replace(kv[i], kv[i + 1]);
        return out;
    }
}
