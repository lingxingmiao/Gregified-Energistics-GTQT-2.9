package com.walhay.gregifiedenergistics.mixins.gtceu.recipes;

import com.walhay.gregifiedenergistics.mixins.interfaces.IResearchRecipeMapAccessor;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.machines.RecipeMapAssemblyLine;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeMapAssemblyLine.class)
public class RecipeMapMixin implements IResearchRecipeMapAccessor {

    @Unique private static int id = 0;

    @Unique private final Int2ObjectOpenHashMap<Recipe> idToRecipeMap = new Int2ObjectOpenHashMap<>();

    @Unique private final Object2IntOpenHashMap<Recipe> recipeToIdMap = new Object2IntOpenHashMap<>();

    @Unique private static int autoId() {
        return id++;
    }

    @Override
    public int getIdFromRecipe(Recipe recipe) {
        return recipeToIdMap.getOrDefault(recipe, -1);
    }

    @Override
    @Unique
    public Recipe getRecipeById(int id) {
        return idToRecipeMap.getOrDefault(id, null);
    }

    /**
     * GE-2.9: upstream used {@code @At(RETURN, ordinal = 1)} because 2.8's
     * {@code compileRecipe} had 4 returns and ordinal 1 was the "has research"
     * branch. GTQT 2.9 has only 2 returns, so ordinal 1 is now the generic
     * {@code return true} and would register EVERY assembly-line recipe into
     * idToRecipeMap (unbounded growth + false getIdFromRecipe hits).
     *
     * <p>Inject at RETURN instead and register only recipes that actually carry
     * the {@link ResearchProperty}, preserving the original semantics.
     */
    @Inject(method = "compileRecipe", at = @At("RETURN"), remap = false, require = 0)
    public void injectRecipeId(Recipe recipe, CallbackInfoReturnable<Boolean> cib) {
        if (recipe == null || !recipe.hasProperty(ResearchProperty.getInstance())) {
            return;
        }
        int recipeId = autoId();
        idToRecipeMap.put(recipeId, recipe);
        recipeToIdMap.put(recipe, recipeId);
    }
}
