package com.talhanation.recruits.network;

import com.talhanation.recruits.CommandEvents;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

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
        // This will be used the next time a movement command is issued
        CommandEvents.saveFormation(context.getSender(), this.formation);
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


