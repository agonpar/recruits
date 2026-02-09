package com.talhanation.recruits.network;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.world.GroupPositionData;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageUpdateGroupPositions implements Message<MessageUpdateGroupPositions> {

    private CompoundTag positionsData;

    public MessageUpdateGroupPositions() {
        this.positionsData = new CompoundTag();
    }

    public MessageUpdateGroupPositions(List<GroupPositionData> positions) {
        this.positionsData = serializePositions(positions);
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.CLIENT;
    }

    @Override
    public void executeClientSide(NetworkEvent.Context context) {
        List<GroupPositionData> positions = deserializePositions(positionsData);

        // Update client manager with new positions
        ClientManager.groupPositions.clear();
        for (GroupPositionData data : positions) {
            ClientManager.groupPositions.put(data.getGroupUUID(), data);
        }
    }

    @Override
    public MessageUpdateGroupPositions fromBytes(FriendlyByteBuf buf) {
        this.positionsData = buf.readNbt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(positionsData);
    }

    private static CompoundTag serializePositions(List<GroupPositionData> positions) {
        CompoundTag compound = new CompoundTag();
        ListTag list = new ListTag();

        for (GroupPositionData data : positions) {
            list.add(data.toNBT());
        }

        compound.put("Positions", list);
        return compound;
    }

    private static List<GroupPositionData> deserializePositions(CompoundTag compound) {
        List<GroupPositionData> positions = new ArrayList<>();

        if (compound != null && compound.contains("Positions", Tag.TAG_LIST)) {
            ListTag list = compound.getList("Positions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                positions.add(GroupPositionData.fromNBT(tag));
            }
        }

        return positions;
    }
}

