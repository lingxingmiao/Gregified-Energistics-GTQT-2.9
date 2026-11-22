package com.walhay.gregifiedenergistics.mixins.gtceu.pattern;

import com.walhay.gregifiedenergistics.common.metatileentities.GregifiedEnergisticsMetaTileEntities;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.common.metatileentities.MetaTileEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GE-2.9: makes GTQT 2.9's assembly-line structure accept GE's ME assembly-line
 * buses at the 'I' (item import) position.
 *
 * <p>GTCEu 2.8's {@code AssemblyLineMixin} changed the structure string and
 * injected a {@code FactoryBlockPattern.where(char, TraceabilityPredicate)}
 * predicate. GTQT 2.9 rewrote the whole framework: the assembly line is now
 * declared with {@link DeclarativePatternBuilder} and its 'I' slot is
 * {@code .metaTileEntities('I', MetaTileEntities.ITEM_IMPORT_BUS[ULV])}.
 *
 * <p>Instead of re-declaring the entire structure (fragile, timing-dependent),
 * we widen exactly that one varargs array: when a builder calls
 * {@code metaTileEntities('I', <the ULV item import bus>)} we append GE's two
 * ME assembly-line buses. The structure definition is built lazily via
 * {@code TemplatePool}'s {@code SoftReferenceHolder}, i.e. the factory runs on
 * first structure check - well after late mixins are applied - so there is no
 * class-load ordering problem.
 *
 * <p>The condition is deliberately narrow (symbol 'I' AND the array is exactly
 * the single ULV item import bus) so no other multiblock's pattern is touched.
 */
@Mixin(DeclarativePatternBuilder.class)
public class DeclarativePatternBuilderMixin {

    @ModifyVariable(
            method = "metaTileEntities(C[Lgregtech/api/metatileentity/MetaTileEntity;)" +
                    "Lgregtech/api/pattern/casing/DeclarativePatternBuilder;",
            at = @At("HEAD"),
            ordinal = 0,
            remap = false,
            require = 0,
            argsOnly = true)
    private MetaTileEntity[] ge$extendAssemblyLineImportBus(MetaTileEntity[] original) {
        // Only widen the exact single-element ULV item-import-bus array that
        // the assembly line passes as its 'I' slot. No other multiblock uses
        // precisely this array, so no symbol check is needed (and the handler
        // stays a canonical single-arg @ModifyVariable).
        if (original == null || original.length != 1) {
            return original;
        }
        // Only the assembly line uses this exact single-element array.
        MetaTileEntity[] ulvBus = MetaTileEntities.ITEM_IMPORT_BUS;
        if (ulvBus == null || ulvBus.length == 0 || original[0] != ulvBus[0]) {
            return original;
        }
        MetaTileEntity meBus = GregifiedEnergisticsMetaTileEntities.ME_ASSEMBLY_LINE_BUS;
        MetaTileEntity meOpticalBus = GregifiedEnergisticsMetaTileEntities.ME_ASSEMBLY_LINE_OPTICAL_BUS;
        if (meBus == null || meOpticalBus == null) {
            return original; // GE MTEs not registered yet; leave untouched
        }
        List<MetaTileEntity> extended = new ArrayList<>(Arrays.asList(original));
        extended.add(meBus);
        extended.add(meOpticalBus);
        return extended.toArray(new MetaTileEntity[0]);
    }
}
