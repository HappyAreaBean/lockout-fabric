package me.marin.lockout;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.kill.KillColoredSheepGoal;
import me.marin.lockout.lockout.goals.obtain.Obtain64ColoredConcreteGoal;
import me.marin.lockout.lockout.goals.obtain.Obtain64ColoredWoolGoal;
import me.marin.lockout.lockout.goals.obtain.ObtainColoredGlazedTerracottaGoal;
import me.marin.lockout.lockout.goals.util.GoalDataConstants;
import me.marin.lockout.lockout.goals.wear_armor.WearColoredLeatherPieceGoal;
import me.marin.lockout.lockout.interfaces.ObtainAllItemsGoal;
import me.marin.lockout.lockout.interfaces.RequiresAmount;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public class LockoutTranslation {

    public static String goalIdToString(Goal goal) {
        var id = goal.getId();

        return "lockout.goal." + id.toLowerCase();
    }

    public static String getKey(KeyType type, String value) {
        return type.getKeyWithSuffix() + value;
    }

    public static MutableComponent translatable(KeyType type, String value, String fallback, Object... args) {
        return Component.translatableWithFallback(type.getKeyWithSuffix() + value.toLowerCase(Locale.ROOT), fallback, args);
    }

    public static MutableComponent translatable(KeyType type, String value, String fallback) {
        return Component.translatableWithFallback(type.getKeyWithSuffix() + value.toLowerCase(Locale.ROOT), fallback);
    }

    public static MutableComponent getTranslationFromGoal(Goal goal) {
        MutableComponent translate = LockoutTranslation.translatable(LockoutTranslation.KeyType.GOAL, goal.getId(), goal.getGoalName());

        if (goal instanceof KillColoredSheepGoal coloredSheepGoal) {
            var dyeName = coloredSheepGoal.getDyeColor().getName();
            translate = LockoutTranslation.translatable(
                    LockoutTranslation.KeyType.GOAL,
                    goal.getId(),
                    goal.getGoalName(),
                    LockoutTranslation.translatable(LockoutTranslation.KeyType.COLOR, dyeName, dyeName)
            );
        }

        if (goal instanceof ObtainAllItemsGoal allItemsGoal) {
            if (goal instanceof WearColoredLeatherPieceGoal wearColoredLeatherPieceGoal) {
                var data = wearColoredLeatherPieceGoal.getData();
                var parts = data.split(GoalDataConstants.DATA_SEPARATOR);
                var leatherArmor = GoalDataConstants.getLeatherArmor(parts[0]);
                var dyeColor = GoalDataConstants.getDyeColor(parts[1]);

                translate = LockoutTranslation.translatable(LockoutTranslation.KeyType.GOAL,
                        goal.getId(),
                        goal.getGoalName(),
                        LockoutTranslation.translatable(LockoutTranslation.KeyType.COLOR, dyeColor.getName(), GoalDataConstants.getDyeColorFormatted(dyeColor)),
                        Component.translatable(leatherArmor.getDescriptionId())
                );
            }
            if (goal instanceof RequiresAmount) {
                MutableComponent item = Component.empty();

                if (goal instanceof Obtain64ColoredConcreteGoal) {
                    item = Component.translatable(Obtain64ColoredConcreteGoal.getConcreteColor(goal.getData()).getDescriptionId());
                }

                if (goal instanceof Obtain64ColoredWoolGoal) {
                    item = Component.translatable(Obtain64ColoredWoolGoal.getWoolColor(goal.getData()).getDescriptionId());
                }

                translate = LockoutTranslation.translatable(LockoutTranslation.KeyType.GOAL, "obtain_64",
                        goal.getGoalName(), item);
            }

            if (goal instanceof ObtainColoredGlazedTerracottaGoal terracottaGoal) {
                var item = ObtainColoredGlazedTerracottaGoal.getGlazedTerracottaColor(terracottaGoal.getData());
                if (item != null) {
                    String key = item.getDescriptionId();
                    translate = LockoutTranslation.translatable(LockoutTranslation.KeyType.GOAL,
                            goal.getId(),
                            goal.getGoalName(),
                            Component.translatable(key));
                }
            }
        }

        return translate;
    }

    @RequiredArgsConstructor
    @Getter
    public enum KeyType {
        COLOR("lockout.color"),
        BUILDER("lockout.builder"),
        MISC("lockout.misc"),
        GOAL("lockout.goal"),
        ;

        /**
         * translation key
         */
        private final String key;

        /**
         * Get translation key with "." suffix added
         * @return "'translation key'."
         */
        public String getKeyWithSuffix() {
            return key + ".";
        }
    }

}
