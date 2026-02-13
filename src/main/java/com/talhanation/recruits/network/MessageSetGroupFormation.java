package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.util.FormationUtils;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
            // Use the same method as "hold your position" command to calculate center
            Vec3 centerPos = FormationUtils.getGeometricMedian(recruits, (ServerLevel) context.getSender().getCommandSenderWorld());

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


