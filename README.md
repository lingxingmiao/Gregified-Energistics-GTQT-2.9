# Gregified Energistics — GTQT 2.9 Port

A port of [WALHAY/Gregified-Energistics](https://github.com/WALHAY/Gregified-Energistics) 0.1.4
to **GTQT (GTCEu 2.9.0)** for Minecraft 1.12.2.

This repository is a community-maintained compatibility port. It is NOT affiliated
with the original author WALHAY or the GTQT/GTCEu teams.

## Why this port exists

GE 0.1.4 was compiled against GTCEu 2.8.10. On GTQT 2.9 it crashes with:

```
NoClassDefFoundError: .../multiblockparts/MTEMEAssemblyLineBus
Caused by: ClassNotFoundException: Found ...MTEAbstractAssemblyLineBus in invalid classes
```

Root cause: GE's `MTEAbstractAssemblyLineBus -> MetaTileEntityCraftingProvider ->
gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart`.
GTQT 2.9 **deleted** that whole `multiblockpart/appeng` package (it kept only ME
texture constants and `AE2CompatOptions` config; there is no rewritten AE2 host
class to map onto). Without a parent class the JVM marks the GE class invalid.

## What changed vs upstream 0.1.4

| Area | Change |
|------|--------|
| AE2 host base | Vendored `MetaTileEntityAEHostablePart` into GE's own package (`api/metatileentity`), reconstructed from the 2.8.10 source. Dropped 2.9-removed callbacks (`getCableConnectionType`, `gridChanged`), replaced `ConfigHolder.compat.ae2.*` with local defaults, widened `clearMachineInventory` to `List`, rewrote Java-16 pattern matching to Java 8. |
| `MetaTileEntityCraftingProvider` | Now extends the vendored base instead of the deleted GT class. |
| Structure mixin | `AssemblyLineMixin` targeted 2.8 `FactoryBlockPattern`/`TraceabilityPredicate` (rewritten into `StructureRuntime`/`PieceTemplate` in 2.9). Disabled and kept as `.java.disabled` for a future rewrite. |
| `IMultiblockAbilityPart` | 2.9 added abstract `registerAbilities(AbilityInstances)`; implemented in `MTEAbstractAssemblyLineBus`. |
| `clearMachineInventory` | 2.9 signature is `List<ItemStack>` (was `NonNullList`); updated in both the abstract bus and `MTEMEAssemblyLineBus`. |
| AE2 holder interfaces | **Critical runtime fix**: GTQT 2.9 removed `IActionHost`/`IGridProxyable` from `MetaTileEntityHolder` (and `MetaTileEntity.getProxy()`), which would make `getProxy()` silently return null and leave every ME bus permanently offline. `MetaTileEntityHolderMixin` re-adds the interfaces and forwards to the vendored AE2 part's own `AENetworkProxy`. |
| AE2 config | The vendored base reads the live `ConfigHolder.compat.ae2.updateIntervals` / `.meHatchEnergyUsage` (still present in 2.9) instead of hardcoded constants. |
| GUI | 2.9 removed `Widget.drawItemStack(...)`; `SubstitutionSlotWidget` now draws via Minecraft `RenderItem`. |
| Recipes | 2.9 removed `ADVANCED_DATA_ACCESS_HATCH` and changed `input(MetaTileEntity[])`; now uses `DATA_ACCESS_HATCH[0]` / `DATA_ACCESS_HATCH[last]`. |
| Dependency | `required-after:gregtech@[2.9.0,)`; build compiles against local GTQT 2.9 / AE2 UEL / ModularUI jars in `libs/`. |
| Build | `spotless` no longer gates `build` (offline-friendly). |

## Building

1. Put these three jars into `libs/` (they are NOT committed):
   - `gregtech-gtqt-1.12.2-2.9.0.jar` (the GTQT 2.9 host)
   - `ae2-uel-v0.56.7.jar` (AE2 UEL / Extended Life)
   - `modularui-3.1.5.jar`
2. Run `./gradlew build`.
3. Deliverable: `build/libs/gregifiedenergistics-0.2.0.jar`.

The jars in `libs/` are dev-only `compileOnly` dependencies; at runtime the mod
expects GTQT 2.9, AE2 and ModularUI to be installed in `mods/`.

## Known limitations

- The assembly-line structure predicate mixin is disabled, so the ME assembly-line
  bus is not auto-injected into the 2.9 assembly-line structure definition. The
  buses themselves register and work; hooking them into the 2.9
  `DeclarativePatternBuilder` structure is future work.
- The vendored AE2 host base is a minimal reconstruction: it provides the AE2 grid
  proxy / monitor / online-status plumbing GE needs, without the 2.8-only TOP/Waila
  probe callbacks.
- NBT/chunk-lifecycle AE2 proxy hooks (`readFromNBT_AENetwork`, `invalidateAE`,
  `onChunkUnloadAE`) that GTCEu 2.8 called on the holder are NOT re-added; the
  AE2 proxy may not persist its grid state across chunk unload/reload. Wiring
  those into the 2.9 holder lifecycle is future work.

## License

MIT (see [LICENSE.md](LICENSE.md)), inherited from upstream.
