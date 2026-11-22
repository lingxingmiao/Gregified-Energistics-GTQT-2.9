package com.walhay.gregifiedenergistics.mixins.gtceu.metatileentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.GridAccessException;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import com.walhay.gregifiedenergistics.api.metatileentity.ICraftingProviderAccessor;
import com.walhay.gregifiedenergistics.api.metatileentity.MetaTileEntityAEHostablePart;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GE-2.9: restores the AE2 interfaces that GTQT 2.9 removed from
 * {@link MetaTileEntityHolder}.
 *
 * <p>In GTCEu 2.8 the holder implemented {@code IActionHost} and
 * {@code IGridProxyable} and forwarded everything to
 * {@code metaTileEntity.getProxy()} / {@code metaTileEntity.getCableConnectionType()}.
 * GTQT 2.9 deleted both the interfaces and those {@code MetaTileEntity} methods.
 * Without them, {@link MetaTileEntityAEHostablePart#getProxy()} sees a holder that
 * is not an {@code IGridProxyable}, returns null, and every ME bus stays offline.
 *
 * <p>This mixin re-adds the interfaces and forwards to the GE AE2 part itself
 * (which now owns its own {@link AENetworkProxy}). Non-GE machines keep the
 * neutral behaviour (no cable connection, no grid node), matching 2.8's
 * "metaTileEntity == null" path.
 */
/**
 * The mixin class implements the interfaces directly (no {@code @Implements}):
 * AE2 is a hard dependency of GE and present at compile time, so Mixin merges
 * these methods into the target class under their real names. Using
 * {@code @Implements(prefix=...)} would require {@code prefix$method} names and
 * is only needed for optional (soft) dependencies.
 */
@Mixin(MetaTileEntityHolder.class)
public abstract class MetaTileEntityHolderMixin
        implements ICraftingProvider, IActionHost, IGridProxyable {

    @Shadow(remap = false)
    MetaTileEntity metaTileEntity;

    /** Returns the GE AE2 part attached to this holder, or null for any other machine. */
    private MetaTileEntityAEHostablePart<?> geAEPart() {
        return metaTileEntity instanceof MetaTileEntityAEHostablePart
                ? (MetaTileEntityAEHostablePart<?>) metaTileEntity
                : null;
    }

    // ---- ICraftingProvider (unchanged from upstream) ------------------------

    @Override
    public boolean isBusy() {
        return metaTileEntity == null || ((ICraftingProviderAccessor) metaTileEntity).isBusy();
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting inventory) {
        return metaTileEntity != null && ((ICraftingProviderAccessor) metaTileEntity).pushPattern(details, inventory);
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        if (metaTileEntity != null) ((ICraftingProviderAccessor) metaTileEntity).provideCrafting(helper);
    }

    // ---- IGridProxyable / IGridHost / IActionHost --------------------------

    @Override
    public AENetworkProxy getProxy() {
        MetaTileEntityAEHostablePart<?> part = geAEPart();
        return part == null ? null : part.getProxy();
    }

    @Override
    public DimensionalCoord getLocation() {
        // 2.8 used `new DimensionalCoord(this)` (the holder is a TileEntity).
        // In 2.9 we build it from the MTE's world/pos instead; this ctor exists in AE2 UEL.
        if (metaTileEntity == null) return null;
        World world = metaTileEntity.getWorld();
        BlockPos pos = metaTileEntity.getPos();
        return world == null || pos == null ? null : new DimensionalCoord(world, pos);
    }

    @Override
    public void gridChanged() {
        // AE2 calls this when the grid topology changes. The GE part does not
        // need to react (2.8's MetaTileEntity.gridChanged() was a no-op too).
    }

    @Override
    public IGridNode getGridNode(AEPartLocation part) {
        if (getCableConnectionType(part) == AECableType.NONE) return null;
        AENetworkProxy proxy = getProxy();
        return proxy == null ? null : proxy.getNode();
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation part) {
        MetaTileEntityAEHostablePart<?> ge = geAEPart();
        if (ge == null) return AECableType.NONE;
        // 2.8 forwarded to metaTileEntity.getCableConnectionType(part); the GE
        // part accepts SMART only on its front face.
        return part.getFacing() == ge.getFrontFacing() ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void securityBreak() {}

    @Override
    public IGridNode getActionableNode() {
        AENetworkProxy proxy = getProxy();
        return proxy == null ? null : proxy.getNode();
    }

    /** Exposed for GE code that needs the grid (e.g. MENetworkCraftingPatternChange). */
    public IGrid getGrid() {
        AENetworkProxy proxy = getProxy();
        if (proxy == null) return null;
        try {
            return proxy.getGrid();
        } catch (GridAccessException ignored) {
            return null;
        }
    }

    // ---- AE2 proxy lifecycle -------------------------------------------------
    //
    // GTCEu 2.8 called readFromNBT_AENetwork / writeToNBT_AENetwork /
    // invalidateAE / onChunkUnloadAE from the holder. GTQT 2.9 removed all four.
    // Without them the AENetworkProxy node id/channel is never saved (ME buses
    // lose their network on world reload) and nodes are never destroyed (GridNode
    // leak on block break / chunk unload). Re-add them here, forwarding to the
    // GE AE2 part's own proxy.

    // require=0: if a GTQT build renames these lifecycle methods the mixin still
    // applies (the AE2 interfaces above remain), just without proxy persistence.
    // Prefer a soft failure over blocking startup.
    @Inject(method = "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
            at = @At("RETURN"), remap = false, require = 0)
    private void ge$writeAEToNBT(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        AENetworkProxy proxy = getProxy();
        if (proxy != null) {
            proxy.writeToNBT(compound);
        }
    }

    @Inject(method = "readFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void ge$readAEFromNBT(NBTTagCompound compound, CallbackInfo ci) {
        AENetworkProxy proxy = getProxy();
        if (proxy != null) {
            proxy.readFromNBT(compound);
        }
    }

    @Inject(method = "invalidate()V", at = @At("HEAD"), remap = false, require = 0)
    private void ge$invalidateAE(CallbackInfo ci) {
        AENetworkProxy proxy = getProxy();
        if (proxy != null) {
            proxy.invalidate();
        }
    }

    @Inject(method = "onChunkUnload()V", at = @At("HEAD"), remap = false, require = 0)
    private void ge$onChunkUnloadAE(CallbackInfo ci) {
        AENetworkProxy proxy = getProxy();
        if (proxy != null) {
            proxy.onChunkUnload();
        }
    }
}
