package me.marin.lockout;

import lombok.Getter;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.interfaces.HasTooltipInfo;
import me.marin.lockout.network.UpdateTooltipPayload;
import me.marin.lockout.server.LockoutServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import java.util.*;

public class LockoutTeamServer extends LockoutTeam {

    private final Map<UUID, String> playerNameMap = new HashMap<>();
    @Getter
    private final MinecraftServer server;

    public LockoutTeamServer(List<String> playerNames, ChatFormatting formattingColor, MinecraftServer server) {
        super(playerNames, new ArrayList<>(), formattingColor);
        this.server = server;

        PlayerList manager = server.getPlayerList();

        // All players from playerNames are online at this moment.
        for (String playerName : playerNames) {
            UUID uuid = manager.getPlayerByName(playerName).getUUID();
            this.getPlayerIds().add(uuid);
            this.playerNameMap.put(uuid, playerName);
        }
    }

    public String getPlayerName(UUID uuid) {
        return playerNameMap.get(uuid);
    }

    public void sendMessage(String message) {
        for (UUID uuid : getPlayerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    public void sendMessage(Component message) {
        for (UUID uuid : getPlayerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    public void sendTooltipUpdate(Goal goal) {
        sendTooltipUpdate(goal, true);
    }
    public void sendTooltipUpdate(Goal goal, boolean updateSpectators) {
        if (!(goal instanceof HasTooltipInfo tooltipGoal)) return;
        for (UUID playerId : getPlayerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            var payload = new UpdateTooltipPayload(goal.getId(), String.join("\n", tooltipGoal.getTooltip(this, player)));
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }

        if (updateSpectators) {
            this.sendTooltipPacketSpectators(goal);
        }
    }
    private void sendTooltipPacketSpectators(Goal goal) {
        if (!(goal instanceof HasTooltipInfo tooltipGoal)) return;
        var payload = new UpdateTooltipPayload(goal.getId(), String.join("\n", tooltipGoal.getSpectatorTooltip()));
        for (ServerPlayer spectator : Utility.getSpectators(LockoutServer.lockout, server)) {
            ServerPlayNetworking.send(spectator, payload);
        }
    }

}
