package ru.optimist.autoslab;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AutoSlabClient implements ClientModInitializer {

    public static final String MOD_ID = "autoslab";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Категория клавиши в меню "Управление" (с 1.21.9 это отдельный объект, а не строка). */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    /** Максимальная дистанция до блока, дальше которой мод не будет пытаться поставить полублок. */
    private static final double MAX_REACH = 4.5;

    private static AutoSlabConfig config;
    private static KeyBinding activateKey;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        config = AutoSlabConfig.load();

        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoslab.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // не привязана по умолчанию - настраивается в Управление -> AutoSlab
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);

        LOGGER.info("AutoSlab загружен. Настройте клавишу в Управление -> AutoSlab, интервал/радиус - командой /autoslab либо через Mod Menu");
    }

    public static AutoSlabConfig getConfig() {
        return config;
    }

    public static KeyBinding getActivateKey() {
        return activateKey;
    }

    private void onClientTick(MinecraftClient client) {
        if (!config.enabled) {
            return;
        }
        if (client.player == null || client.world == null) {
            tickCounter = 0;
            return;
        }
        if (!activateKey.isPressed()) {
            tickCounter = 0;
            return;
        }

        tickCounter++;
        if (tickCounter < config.intervalTicks) {
            return;
        }
        tickCounter = 0;

        tryPlaceOneSlab(client);
    }

    private void tryPlaceOneSlab(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || client.interactionManager == null) {
            return;
        }

        Hand hand = findSlabHand(player);
        if (hand == null) {
            return; // в руках нет полублоков - расставлять нечего
        }

        BlockPos playerPos = player.getBlockPos();
        int r = config.radius;

        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    candidates.add(playerPos.add(x, y, z));
                }
            }
        }

        Vec3d eyePos = player.getEyePos();
        candidates.sort((a, b) -> Double.compare(
                Vec3d.ofCenter(a).squaredDistanceTo(eyePos),
                Vec3d.ofCenter(b).squaredDistanceTo(eyePos)
        ));

        for (BlockPos pos : candidates) {
            if (!isFullBlock(world, pos)) {
                continue;
            }
            BlockPos above = pos.up();
            if (!world.getBlockState(above).isAir()) {
                continue; // сверху уже что-то есть, нечего заменять
            }
            Vec3d hitPoint = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            if (hitPoint.distanceTo(eyePos) > MAX_REACH) {
                continue;
            }

            BlockHitResult hitResult = new BlockHitResult(hitPoint, Direction.UP, pos, false);
            var result = client.interactionManager.interactBlock(player, hand, hitResult);
            if (result.isAccepted()) {
                player.swingHand(hand);
                return; // один полублок за один интервал
            }
        }
    }

    private static boolean isFullBlock(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return net.minecraft.block.Block.isFaceFullSquare(state.getCollisionShape(world, pos), Direction.UP);
    }

    private static Hand findSlabHand(ClientPlayerEntity player) {
        if (isSlabStack(player.getMainHandStack())) {
            return Hand.MAIN_HAND;
        }
        if (isSlabStack(player.getOffHandStack())) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private static boolean isSlabStack(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof SlabBlock;
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher,
                                   net.minecraft.command.CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("autoslab")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.translatable("autoslab.message.usage"));
                    return 0;
                })
                .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                    config.enabled = !config.enabled;
                    config.save();
                    ctx.getSource().sendFeedback(Text.translatable(
                            config.enabled ? "autoslab.message.enabled" : "autoslab.message.disabled"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("interval")
                        .then(ClientCommandManager.argument("ticks", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> {
                                    int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                    config.intervalTicks = ticks;
                                    config.save();
                                    ctx.getSource().sendFeedback(Text.translatable("autoslab.message.interval", ticks));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("radius")
                        .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 8))
                                .executes(ctx -> {
                                    int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
                                    config.radius = blocks;
                                    config.save();
                                    ctx.getSource().sendFeedback(Text.translatable("autoslab.message.radius", blocks));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.translatable(
                            "autoslab.message.status",
                            config.enabled ? "вкл" : "выкл",
                            config.intervalTicks,
                            config.radius));
                    return 1;
                }))
        );
    }
}