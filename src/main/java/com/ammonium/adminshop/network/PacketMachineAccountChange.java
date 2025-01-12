package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.AutoShopMachine;
import com.ammonium.adminshop.money.BankAccount;
import com.ammonium.adminshop.money.MoneyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class PacketMachineAccountChange {
    private final String machineOwner;
    private final String accOwner;
    private final int accID;
    private final BlockPos pos;

    public PacketMachineAccountChange(String machineOwner, String accOwner, int accID, BlockPos pos) {
        this.machineOwner = machineOwner;
        this.accOwner = accOwner;
        this.accID = accID;
        this.pos = pos;
    }
    public PacketMachineAccountChange(FriendlyByteBuf buf) {
        this.machineOwner = buf.readUtf();
        this.accOwner = buf.readUtf();
        this.accID = buf.readInt();
        this.pos = buf.readBlockPos();
    }
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.machineOwner);
        buf.writeUtf(this.accOwner);
        buf.writeInt(this.accID);
        buf.writeBlockPos(this.pos);

    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            //Client side accessed here
            //Do NOT call client-only code though, since server needs to access this too

            // Change machine's account
            ServerPlayer player = ctx.getSender();

            if (player != null) {
                System.out.println("Changing machine account for "+this.pos+" to "+this.accOwner+":"+this.accID);
                // Get SellerBE
                Level level = player.level;
                BlockEntity blockEntity = level.getBlockEntity(this.pos);
                if (!(blockEntity instanceof AutoShopMachine machineEntity)) {
                    AdminShop.LOGGER.error("BlockEntity at pos is not MachineWithOwnerAndAccount");
                    return;
                }
                // Check machine's owner is the same as player
                if (!machineEntity.getOwnerUUID().equals(player.getStringUUID())) {
                    AdminShop.LOGGER.error("Player is not the machine's owner");
                    return;
                }
                // Check if chosen new account is in player's usable accounts
                MoneyManager moneyManager = MoneyManager.get(player.getLevel());
                List<BankAccount> usableAccounts = moneyManager.getSharedAccounts().get(player.getStringUUID());
                Optional<BankAccount> search = usableAccounts.stream().filter(account -> Pair.of(accOwner, accID)
                        .equals(Pair.of(account.getOwner(), account.getId()))).findAny();
                if (search.isEmpty()) {
                    AdminShop.LOGGER.error("Player does not have access to that account");
                    return;
                }
                System.out.println("Saving machine account information.");

                // Apply changes to machineEntity
                machineEntity.setAccount(Pair.of(this.accOwner, this.accID));
                blockEntity.setChanged();
                machineEntity.sendUpdates();
            }
        });
        return true;
    }
}
