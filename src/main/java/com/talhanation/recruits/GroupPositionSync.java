package com.talhanation.recruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.network.MessageUpdateGroupPositions;
import com.talhanation.recruits.world.GroupPositionData;
import com.talhanation.recruits.world.RecruitsGroup;
import com.talhanation.recruits.world.RecruitsGroupsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class GroupPositionSync {

    private static final int UPDATE_INTERVAL = 100;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        if (event.getServer() != null) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                updateGroupPositionsForPlayer(player);
            }
        }
    }

    private void updateGroupPositionsForPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RecruitsGroupsManager groupsManager = RecruitEvents.recruitsGroupsManager;

        if (groupsManager == null) return;

        List<RecruitsGroup> playerGroups = groupsManager.getPlayerGroups(player);
        if (playerGroups == null || playerGroups.isEmpty()) return;

        List<GroupPositionData> positions = new ArrayList<>();

        for (RecruitsGroup group : playerGroups) {
            AABB searchBox = new AABB(
                player.getX() - 100000, level.getMinBuildHeight(), player.getZ() - 100000,
                player.getX() + 100000, level.getMaxBuildHeight(), player.getZ() + 100000
            );

            List<AbstractRecruitEntity> groupRecruits = level.getEntitiesOfClass(
                AbstractRecruitEntity.class,
                searchBox,
                recruit -> {
                    UUID recruitGroup = recruit.getGroup();
                    return recruitGroup != null && recruitGroup.equals(group.getUUID())
                        && recruit.getOwnerUUID() != null && recruit.getOwnerUUID().equals(player.getUUID());
                }
            );

            if (!groupRecruits.isEmpty()) {
                double centerX = 0;
                double centerZ = 0;

                for (AbstractRecruitEntity recruit : groupRecruits) {
                    centerX += recruit.getX();
                    centerZ += recruit.getZ();
                }

                centerX /= groupRecruits.size();
                centerZ /= groupRecruits.size();

                positions.add(new GroupPositionData(
                    group.getUUID(),
                    centerX,
                    centerZ,
                    groupRecruits.size()
                ));
            }
        }

        if (!positions.isEmpty()) {
            Main.SIMPLE_CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new MessageUpdateGroupPositions(positions)
            );
        }
    }
}


