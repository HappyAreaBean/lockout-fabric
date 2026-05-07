package me.marin.lockout;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.marin.lockout.lockout.DefaultGoalRegister;
import me.marin.lockout.network.CustomBoardPayload;
import me.marin.lockout.network.Networking;
import me.marin.lockout.server.LockoutServer;
import me.marin.lockout.util.PlayerSuggestionProvider;
import me.marin.lockout.util.TeamSuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static me.marin.lockout.Constants.MAX_BOARD_SIZE;
import static me.marin.lockout.Constants.NAMESPACE;
import static me.marin.lockout.Constants.PLACEHOLDER_PERM_STRING;

public class LockoutInitializer implements ModInitializer {

    private static final Predicate<CommandSourceStack> PERMISSIONS = (ssc) ->
            ssc.getServer() != null && (Permissions.check(ssc, PLACEHOLDER_PERM_STRING, LevelBasedPermissionSet.GAMEMASTER.level()) || ssc.getServer().isSingleplayer());

    public static Version MOD_VERSION;

    @Override
    public void onInitialize() {
        MOD_VERSION = FabricLoader.getInstance().getModContainer(NAMESPACE).get().getMetadata().getVersion();

        LockoutConfig.load();
        GoalPoolConfig.load();
        Networking.registerPayloads();
        DefaultGoalRegister.registerGoals();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            {
                {
                    var commandNode = Commands.literal("GoalDataGenerate").requires(PERMISSIONS).executes(s -> {
                        new GoalDataGenerator();
                        s.getSource().sendSystemMessage(Component.literal("Check console for output."));
                        return 1;
                    }).build();

                    dispatcher.getRoot().addChild(commandNode);
                }

                {
                    // Lockout command
                    var commandNode = Commands.literal("lockout").requires(PERMISSIONS).build();
                    var teamsNode = Commands.literal("teams").build();
                    var playersNode = Commands.literal("players").build();
                    //TODO make custom argument types
                    var teamListNode = Commands.argument("team names", StringArgumentType.greedyString()).suggests(new TeamSuggestionProvider()).executes(LockoutServer::lockoutCommandLogic).build();
                    var playerListNode = Commands.argument("player names", StringArgumentType.greedyString()).suggests(new PlayerSuggestionProvider()).executes(LockoutServer::lockoutCommandLogic).build();

                    dispatcher.getRoot().addChild(commandNode);
                    commandNode.addChild(teamsNode);
                    commandNode.addChild(playersNode);
                    teamsNode.addChild(teamListNode);
                    playersNode.addChild(playerListNode);
                }


                {
                    // Blackout command
                    var commandNode = Commands.literal("blackout").requires(PERMISSIONS).build();
                    var teamNode = Commands.literal("team").build();
                    var playersNode = Commands.literal("players").build();
                    //TODO make custom argument types
                    var teamNameNode = Commands.argument("team name", StringArgumentType.greedyString()).suggests(new TeamSuggestionProvider()).executes(LockoutServer::blackoutCommandLogic).build();
                    var playerListNode = Commands.argument("player names", StringArgumentType.greedyString()).suggests(new PlayerSuggestionProvider()).executes(LockoutServer::blackoutCommandLogic).build();

                    dispatcher.getRoot().addChild(commandNode);
                    commandNode.addChild(teamNode);
                    commandNode.addChild(playersNode);
                    teamNode.addChild(teamNameNode);
                    playersNode.addChild(playerListNode);
                }
            }


            {
                // Chat command
                var chatCommandNode = Commands.literal("chat").build();
                var chatTeamNode = Commands.literal("team").executes(context -> LockoutServer.setChat(context, ChatManager.Type.TEAM)).build();
                var chatLocalNode = Commands.literal("local").executes(context -> LockoutServer.setChat(context, ChatManager.Type.LOCAL)).build();

                dispatcher.getRoot().addChild(chatCommandNode);
                chatCommandNode.addChild(chatTeamNode);
                chatCommandNode.addChild(chatLocalNode);
            }


            {
                // GiveGoal command
                var giveGoalRoot = Commands.literal("GiveGoal").requires(PERMISSIONS).build();
                var playerName = Commands.argument("player name", GameProfileArgument.gameProfile()).build();
                var goalIndex = Commands.argument("goal number", IntegerArgumentType.integer(1, MAX_BOARD_SIZE * MAX_BOARD_SIZE)).executes(LockoutServer::giveGoal).build();

                dispatcher.getRoot().addChild(giveGoalRoot);
                giveGoalRoot.addChild(playerName);
                playerName.addChild(goalIndex);
            }

            {
                // SetStartTime command
                var setStartTimeRoot = Commands.literal("SetStartTime").requires(PERMISSIONS).build();
                var seconds = Commands.argument("seconds", IntegerArgumentType.integer(5, 300)).executes(LockoutServer::setStartTime).build();

                dispatcher.getRoot().addChild(setStartTimeRoot);
                setStartTimeRoot.addChild(seconds);
            }

            {
                // RemoveCustomBoard command (SetCustomBoard is registered in LockoutClient, and server listens for a packet)

                dispatcher.getRoot().addChild(Commands.literal("RemoveCustomBoard").requires(PERMISSIONS).executes((context) -> {
                    ClientPlayNetworking.send(new CustomBoardPayload(Optional.empty()));
                    return 1;
                }).build());
            }

            {
                // SetBoardSize command

                var setBoardTimeRoot = Commands.literal("SetBoardSize").requires(PERMISSIONS).build();
                var size = Commands.argument("board size", IntegerArgumentType.integer(3, 7)).executes(LockoutServer::setBoardSize).build();

                dispatcher.getRoot().addChild(setBoardTimeRoot);
                setBoardTimeRoot.addChild(size);
            }

            {
                // SetGiveCompasses command

                var setGiveCompassesRoot = Commands.literal("SetGiveCompasses").requires(PERMISSIONS).build();
                var giveCompasses = Commands.argument("giveCompasses", BoolArgumentType.bool()).executes(LockoutServer::setGiveCompasses).build();

                dispatcher.getRoot().addChild(setGiveCompassesRoot);
                setGiveCompassesRoot.addChild(giveCompasses);
            }

            {
                // ReloadGoalPool command

                dispatcher.getRoot().addChild(Commands.literal("ReloadGoalPool").requires(PERMISSIONS).executes(LockoutServer::reloadGoalPool).build());
            }

            {
                // GetNearbyStructures command
                dispatcher.getRoot().addChild(Commands.literal("GetNearbyStructures").requires(PERMISSIONS).executes(LockoutServer::getNearbyStructures).build());
            }

            {
                // GetNearbyBiomes command
                dispatcher.getRoot().addChild(Commands.literal("GetNearbyBiomes").requires(PERMISSIONS).executes(LockoutServer::getNearbyBiomes).build());
            }

            {
                // Forfeit command
                dispatcher.getRoot().addChild(Commands.literal("forfeit").executes(LockoutServer::forfeitCommand).build());
            }

        });

        LootTableEvents.REPLACE.register(((key, original, source, registries) -> {
            if (Objects.equals(key, BuiltInLootTables.PIGLIN_BARTERING)) {
                UniformGenerator ironNuggetsCount = UniformGenerator.between(9.0F, 36.0F);
                UniformGenerator quartzCount = UniformGenerator.between(8.0F, 16.0F);
                UniformGenerator glowstoneDustCount = UniformGenerator.between(5.0F, 12.0F);
                UniformGenerator magmaCreamCount = UniformGenerator.between(2.0F, 6.0F);
                UniformGenerator enderPearlCount = UniformGenerator.between(4.0F, 8.0F);
                UniformGenerator stringCount = UniformGenerator.between(8.0F, 24.0F);
                UniformGenerator fireChargeCount = UniformGenerator.between(1.0F, 5.0F);
                UniformGenerator gravelCount = UniformGenerator.between(8.0F, 16.0F);
                UniformGenerator leatherCount = UniformGenerator.between(4.0F, 10.0F);
                UniformGenerator netherBrickCount = UniformGenerator.between(4.0F, 16.0F);
                UniformGenerator cryingObsidianCount = UniformGenerator.between(1.0F, 3.0F);
                UniformGenerator soulSandCount = UniformGenerator.between(4.0F, 16.0F);

                LootPool pool = LootPool.lootPool()
                        .add(LootItem.lootTableItem(Items.BOOK).apply(EnchantRandomlyFunction.randomEnchantment().withEnchantment(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SOUL_SPEED))).setWeight(5))
                        .add(LootItem.lootTableItem(Items.IRON_BOOTS).apply(EnchantRandomlyFunction.randomEnchantment().withEnchantment(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SOUL_SPEED))).setWeight(8))
                        .add(LootItem.lootTableItem(Items.POTION).apply(SetPotionFunction.setPotion(Potions.FIRE_RESISTANCE)).setWeight(10))
                        .add(LootItem.lootTableItem(Items.SPLASH_POTION).apply(SetPotionFunction.setPotion(Potions.FIRE_RESISTANCE)).setWeight(10))
                        .add(LootItem.lootTableItem(Items.IRON_NUGGET).apply(SetItemCountFunction.setCount(ironNuggetsCount)).setWeight(10))
                        .add(LootItem.lootTableItem(Items.QUARTZ).apply(SetItemCountFunction.setCount(quartzCount)).setWeight(20))
                        .add(LootItem.lootTableItem(Items.GLOWSTONE_DUST).apply(SetItemCountFunction.setCount(glowstoneDustCount)).setWeight(20))
                        .add(LootItem.lootTableItem(Items.MAGMA_CREAM).apply(SetItemCountFunction.setCount(magmaCreamCount)).setWeight(20))
                        .add(LootItem.lootTableItem(Items.ENDER_PEARL).apply(SetItemCountFunction.setCount(enderPearlCount)).setWeight(20))
                        .add(LootItem.lootTableItem(Items.STRING).apply(SetItemCountFunction.setCount(stringCount)).setWeight(20))
                        .add(LootItem.lootTableItem(Items.FIRE_CHARGE).apply(SetItemCountFunction.setCount(fireChargeCount)).setWeight(40))
                        .add(LootItem.lootTableItem(Items.GRAVEL).apply(SetItemCountFunction.setCount(gravelCount)).setWeight(40))
                        .add(LootItem.lootTableItem(Items.LEATHER).apply(SetItemCountFunction.setCount(leatherCount)).setWeight(40))
                        .add(LootItem.lootTableItem(Items.NETHER_BRICK).apply(SetItemCountFunction.setCount(netherBrickCount)).setWeight(40))
                        .add(LootItem.lootTableItem(Items.OBSIDIAN).setWeight(40))
                        .add(LootItem.lootTableItem(Items.CRYING_OBSIDIAN).apply(SetItemCountFunction.setCount(cryingObsidianCount)).setWeight(40))
                        .add(LootItem.lootTableItem(Items.SOUL_SAND).apply(SetItemCountFunction.setCount(soulSandCount)).setWeight(40))
                        .build();
                return LootTable.lootTable().pool(pool).build();
            }
            return null;
        }));

    }

}
