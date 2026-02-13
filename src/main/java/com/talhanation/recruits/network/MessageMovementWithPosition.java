package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.util.FormationUtils;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageMovementWithPosition implements Message<MessageMovementWithPosition> {

    private UUID playerUUID;
    private int state;
    private UUID group;
    private int formation;
    private int targetX;
    private int targetZ;

    public MessageMovementWithPosition(){
    }

    public MessageMovementWithPosition(UUID playerUUID, int state, UUID group, int formation, int targetX, int targetZ) {
        this.playerUUID = playerUUID;
        this.state = state;
        this.group = group;
        this.formation = formation;
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context){
        List<AbstractRecruitEntity> list = Objects.requireNonNull(context.getSender())
            .getCommandSenderWorld()
            .getEntitiesOfClass(AbstractRecruitEntity.class, context.getSender().getBoundingBox().inflate(1000));

        list.removeIf(recruit -> !recruit.isEffectedByCommand(this.playerUUID, this.group));

        // Apply formation at the target position
        if (formation != 0 && !list.isEmpty()) {
            // Find proper Y coordinate
            BlockPos blockPos = FormationUtils.getPositionOrSurface(
                context.getSender().getCommandSenderWorld(),
                new BlockPos(targetX, 64, targetZ)
            );
            Vec3 targetPos = new Vec3(targetX, blockPos.getY(), targetZ);

            CommandEvents.applyFormation(formation, list, context.getSender(), targetPos);
        } else {
            // No formation, just move to position
            for (AbstractRecruitEntity recruit : list) {
                BlockPos blockPos = FormationUtils.getPositionOrSurface(
                    recruit.getCommandSenderWorld(),
                    new BlockPos(targetX, 64, targetZ)
                );

                recruit.setMovePos(blockPos);
                recruit.setFollowState(0);
                recruit.setShouldMovePos(true);
            }
        }
    }

    public MessageMovementWithPosition fromBytes(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.state = buf.readInt();
        this.group = buf.readUUID();
        this.formation = buf.readInt();
        this.targetX = buf.readInt();
        this.targetZ = buf.readInt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.playerUUID);
        buf.writeInt(this.state);
        buf.writeUUID(this.group);
        buf.writeInt(this.formation);
        buf.writeInt(this.targetX);
        buf.writeInt(this.targetZ);
    }
}


