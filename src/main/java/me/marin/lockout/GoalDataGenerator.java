package me.marin.lockout;

import com.google.gson.Gson;
import me.marin.lockout.lockout.GoalRegistry;
import me.marin.lockout.lockout.goals.util.GoalDataConstants;
import me.marin.lockout.lockout.interfaces.HasTooltipInfo;
import net.minecraft.world.item.DyeColor;
import org.apache.commons.lang3.text.WordUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GoalDataGenerator {

    public GoalDataGenerator() {
        var manualEdit = new TreeSet<String>();
        var tooltips = new TreeSet<String>();
        var map = new TreeMap<>();
        var registry = GoalRegistry.INSTANCE.getRegistry();

        registry.keySet().forEach(goal -> {
            Optional<me.marin.lockout.generator.GoalDataGenerator> gen = GoalRegistry.INSTANCE.getDataGenerator(goal);
            String data = gen.map(g -> g.generateData(me.marin.lockout.generator.GoalDataGenerator.ALL_DYES)).orElse(GoalDataConstants.DATA_NONE);

            var nGoal = GoalRegistry.INSTANCE.newGoal(goal, data);

            if (nGoal instanceof HasTooltipInfo) {
                tooltips.add(nGoal.getId());
            }

            if (gen.isPresent()) {
                map.put(LockoutTranslation.goalIdToString(nGoal), nGoal.getId());
                manualEdit.add(nGoal.getId());
                return;
            }

            var displayName = Arrays.stream(nGoal.getId().replace("_", " ").toLowerCase().split(" "))
                    .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(Collectors.joining(" "));

            map.put(LockoutTranslation.goalIdToString(nGoal), displayName);
        });

        String json = new Gson().toJson(map);
        Lockout.log(json);
        Lockout.log("----------");
        Lockout.log("Manual edit required:");
        Lockout.log(String.join(", ", manualEdit));
        Lockout.log("----------");
        Lockout.log("Have Tooltip:");
        Lockout.log(String.join(", ", tooltips));
        Lockout.log("----------");

        var colors = new TreeMap<>();

        for (DyeColor value : DyeColor.values()) {
            var name = value.getName();
            colors.put("lockout.color." + name, WordUtils.capitalizeFully(name.replace("_", " ")));
        }
        String colorsJson = new Gson().toJson(colors);
        Lockout.log(colorsJson);
    }

}
