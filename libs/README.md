# Dev-only compile dependencies

These jars are **NOT committed** (see `.gitignore`). Place them here to build:

| File | Where to get it |
|------|-----------------|
| `gregtech-gtqt-1.12.2-2.9.0.jar` | Your GTQT / GregTech 2.9 build (the mod jar from the pack) |
| `ae2-uel-v0.56.7.jar` | Applied Energistics 2 Unofficial Extended Life |
| `modularui-3.1.5.jar` | ModularUI (GTQT 2.9 ships `IGuiHolder` from it) |

They are declared as `compileOnly files(...)` in
`gradle/scripts/dependencies.gradle`. At runtime the mod expects them supplied by
the modpack; nothing from here is bundled into the GE jar.

The reason we compile against local jars instead of CurseMaven is that GTQT 2.9
is a fork and is not published to the GTCEu 2.8 maven coordinates.
