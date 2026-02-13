package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MessageSetGroupFormation implements Message<MessageSetGroupFormation> {

    private UUID playerUUID;
    private UUID groupUUID;
    private int formation;

    public MessageSetGroupFormation(){
    }

    public MessageSetGroupFormation(UUID playerUUID, UUID groupUUID, int formation) {
        this.playerUUID = playerUUID;
        this.groupUUID = groupUUID;
        this.formation = formation;
    }

    public Dist getExecutingSide() {
        return Dist.DEDICATED_SERVER;
    }

    public void executeServerSide(NetworkEvent.Context context){
        // Save the formation preference for the player
        CommandEvents.saveFormation(context.getSender(), this.formation);

        // Get all recruits that belong to this group
        List<AbstractRecruitEntity> recruits = Objects.requireNonNull(context.getSender())
            .getCommandSenderWorld()
            .getEntitiesOfClass(AbstractRecruitEntity.class, context.getSender().getBoundingBox().inflate(1000));

        // Filter to only recruits that belong to this specific group
        recruits.removeIf(recruit -> !recruit.isEffectedByCommand(this.playerUUID, this.groupUUID));

        if (!recruits.isEmpty()) {
            // Calculate the center position of the group
            double sumX = 0;
            double sumZ = 0;
            for (AbstractRecruitEntity recruit : recruits) {
                sumX += recruit.getX();
                sumZ += recruit.getZ();
            }
            double centerX = sumX / recruits.size();
            double centerZ = sumZ / recruits.size();

            // Use the center position to apply the formation
            net.minecraft.world.phys.Vec3 centerPos = new net.minecraft.world.phys.Vec3(
                centerX,
                recruits.get(0).getY(),
                centerZ
            );

            // Apply the formation at the group's current center position
            CommandEvents.applyFormation(this.formation, recruits, context.getSender(), centerPos);
        }
    }

    public MessageSetGroupFormation fromBytes(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.groupUUID = buf.readUUID();
        this.formation = buf.readInt();
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.playerUUID);
        buf.writeUUID(this.groupUUID);
        buf.writeInt(this.formation);
    }
}


