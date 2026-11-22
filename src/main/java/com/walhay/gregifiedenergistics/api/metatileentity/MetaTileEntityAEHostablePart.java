package com.walhay.gregifiedenergistics.api.metatileentity;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import gregtech.api.capability.IControllable;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockNotifiablePart;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_ONLINE_STATUS;

/**
 * GE-2.9 port of GTCEu 2.8's {@code MetaTileEntityAEHostablePart}.
 *
 * <p>GTQT 2.9 removed the whole
 * {@code gregtech.common.metatileentities.multi.multiblockpart.appeng} package
 * (it did NOT rewrite AE2 integration - it only kept texture/config hooks).
 * Gregified Energistics still needs an AE2-network-aware multiblock part base,
 * so this class is vendored into GE's own package.
 *
 * <p>Changes vs the 2.8 original:
 * <ul>
 *   <li>Package moved to {@code com.walhay.gregifiedenergistics.api.metatileentity}.</li>
 *   <li>Dropped {@code @Override getCableConnectionType(AEPartLocation)} and
 *       {@code gridChanged()} - those were AE2-interface callbacks that GTQT 2.9's
 *       {@code MetaTileEntity} no longer declares; keeping them with {@code @Override}
 *       would not compile. The equivalents can be re-added by AE2 if needed.</li>
 *   <li>{@code ConfigHolder.compat.ae2.*} (removed in 2.9) replaced with local
 *       constants of the same defaults.</li>
 *   <li>{@code clearMachineInventory} parameter widened from {@code NonNullList} to
 *       {@code List} to match the 2.9 {@code MetaTileEntity} signature.</li>
 *   <li>Java 16 pattern-matching {@code instanceof} rewritten to Java 8 syntax.</li>
 * </ul>
 */
public abstract class MetaTileEntityAEHostablePart<T extends IAEStack<T>>
        extends MetaTileEntityMultiblockNotifiablePart implements IControllable {

    // GTQT 2.9 still exposes ConfigHolder.compat.ae2 (updateIntervals /
    // meHatchEnergyUsage), so use the live config instead of hardcoded constants.

    private final Class<? extends IStorageChannel<T>> storageChannel;
    private AENetworkProxy aeProxy;
    private int meUpdateTick;
    protected boolean isOnline;

    public MetaTileEntityAEHostablePart(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch,
                                        Class<? extends IStorageChannel<T>> storageChannel) {
        super(metaTileEntityId, tier, isExportHatch);
        this.meUpdateTick = 0;
        this.storageChannel = storageChannel;
    }

    @Override
    public void update() {
        super.update();
        if (!this.getWorld().isRemote) {
            this.meUpdateTick++;
        }
    }

    /** ME hatches push their buffer back into the ME network on removal, so nothing is dropped. */
    @Override
    public void clearMachineInventory(List<ItemStack> itemBuffer) {}

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        if (this.aeProxy != null) {
            buf.writeBoolean(true);
            NBTTagCompound proxy = new NBTTagCompound();
            this.aeProxy.writeToNBT(proxy);
            buf.writeCompoundTag(proxy);
        } else {
            buf.writeBoolean(false);
        }
        buf.writeInt(this.meUpdateTick);
        buf.writeBoolean(this.isOnline);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        if (buf.readBoolean()) {
            NBTTagCompound nbtTagCompound;
            try {
                nbtTagCompound = buf.readCompoundTag();
            } catch (IOException ignored) {
                nbtTagCompound = null;
            }
            if (this.aeProxy != null && nbtTagCompound != null) {
                this.aeProxy.readFromNBT(nbtTagCompound);
            }
        }
        this.meUpdateTick = buf.readInt();
        this.isOnline = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_ONLINE_STATUS) {
            boolean online = buf.readBoolean();
            if (this.isOnline != online) {
                this.isOnline = online;
                scheduleRenderUpdate();
            }
        }
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        if (this.aeProxy != null) {
            this.aeProxy.setValidSides(EnumSet.of(this.getFrontFacing()));
        }
    }

    /**
     * Returns the AE2 grid proxy, creating it on first use.
     *
     * <p>GE-2.9: in 2.8 the {@code MetaTileEntityHolder} implemented
     * {@code IGridProxyable} and forwarded {@code getProxy()} to
     * {@code metaTileEntity.getProxy()}. GTQT 2.9 removed both the holder's AE2
     * interfaces AND {@code MetaTileEntity.getProxy()}, so the holder mixin now
     * calls this method directly (see {@code MetaTileEntityHolderMixin}).
     */
    @Nullable
    public AENetworkProxy getProxy() {
        if (this.aeProxy == null) {
            this.aeProxy = this.createProxy();
        }
        if (this.aeProxy != null && !this.aeProxy.isReady() && this.getWorld() != null) {
            this.aeProxy.onReady();
        }
        return this.aeProxy;
    }

    /** Updates and returns the ME network online status (server side). */
    public boolean updateMEStatus() {
        if (!getWorld().isRemote) {
            boolean online = this.aeProxy != null && this.aeProxy.isActive() && this.aeProxy.isPowered();
            if (this.isOnline != online) {
                writeCustomData(UPDATE_ONLINE_STATUS, buf -> buf.writeBoolean(online));
                this.isOnline = online;
            }
        }
        return this.isOnline;
    }

    protected boolean shouldSyncME() {
        int interval = ConfigHolder.compat.ae2.updateIntervals;
        return interval > 0 && this.meUpdateTick % interval == 0;
    }

    protected IActionSource getActionSource() {
        Object holder = this.getHolder();
        if (holder instanceof IActionHost) {
            return new MachineSource((IActionHost) holder);
        }
        return new BaseActionSource();
    }

    @Nullable
    private AENetworkProxy createProxy() {
        Object holder = this.getHolder();
        if (holder instanceof IGridProxyable) {
            AENetworkProxy proxy = new AENetworkProxy((IGridProxyable) holder, "mte_proxy",
                    this.getStackForm(), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setIdlePowerUsage(ConfigHolder.compat.ae2.meHatchEnergyUsage);
            proxy.setValidSides(EnumSet.of(this.getFrontFacing()));
            return proxy;
        }
        return null;
    }

    @NotNull
    protected IStorageChannel<T> getStorageChannel() {
        return AEApi.instance().storage().getStorageChannel(storageChannel);
    }

    @Nullable
    protected IMEMonitor<T> getMonitor() {
        AENetworkProxy proxy = getProxy();
        if (proxy == null) return null;
        IStorageChannel<T> channel = getStorageChannel();
        try {
            return proxy.getStorage().getInventory(channel);
        } catch (GridAccessException ignored) {
            return null;
        }
    }
}
