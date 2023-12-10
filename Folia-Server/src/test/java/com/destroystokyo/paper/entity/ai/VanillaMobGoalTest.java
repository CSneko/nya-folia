package com.destroystokyo.paper.entity.ai;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class VanillaMobGoalTest {

    @Test
    public void testKeys() {
        List<GoalKey<?>> deprecated = new ArrayList<>();
        List<GoalKey<?>> keys = new ArrayList<>();
        for (Field field : VanillaGoal.class.getFields()) {
            if (field.getType().equals(GoalKey.class)) {
                try {
                    GoalKey<?> goalKey = (GoalKey<?>) field.get(null);
                    if (field.getAnnotation(Deprecated.class) != null) {
                        deprecated.add(goalKey);
                    } else {
                        keys.add(goalKey);
                    }
                } catch (IllegalAccessException e) {
                    System.out.println("Skipping " + field.getName() + ": " + e.getMessage());
                }
            }
        }

        List<Class<?>> classes;
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().whitelistPackages("net.minecraft").scan()) {
            classes = scanResult.getSubclasses(net.minecraft.world.entity.ai.goal.Goal.class.getName()).loadClasses();
        }

        List<GoalKey<?>> vanillaNames = classes.stream()
            .filter(VanillaMobGoalTest::hasNoEnclosingClass)
            .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
            .filter(clazz -> !net.minecraft.world.entity.ai.goal.WrappedGoal.class.equals(clazz)) // TODO - properly fix
            .map(goalClass -> MobGoalHelper.getKey((Class<? extends net.minecraft.world.entity.ai.goal.Goal>) goalClass))
            .collect(Collectors.toList());

        List<GoalKey<?>> missingFromAPI = new ArrayList<>(vanillaNames);
        missingFromAPI.removeAll(keys);
        missingFromAPI.removeIf(k -> MobGoalHelper.ignored.contains(k.getNamespacedKey().getKey()));
        List<GoalKey<?>> missingFromVanilla = new ArrayList<>(keys);
        missingFromVanilla.removeAll(vanillaNames);

        boolean shouldFail = false;
        if (missingFromAPI.size() != 0) {
            System.out.println("Missing from API: ");
            for (GoalKey<?> key : missingFromAPI) {
                System.out.println("GoalKey<" + key.getEntityClass().getSimpleName() + "> " + key.getNamespacedKey().getKey().toUpperCase() +
                    " = GoalKey.of(" + key.getEntityClass().getSimpleName() + ".class, NamespacedKey.minecraft(\"" + key.getNamespacedKey().getKey() + "\"));");
            }
            shouldFail = true;
        }
        if (missingFromVanilla.size() != 0) {
            System.out.println("Missing from vanilla: ");
            missingFromVanilla.forEach(System.out::println);
            shouldFail = true;
        }

        if (deprecated.size() != 0) {
            System.out.println("Deprecated (might want to remove them at some point): ");
            deprecated.forEach(System.out::println);
        }

        if (shouldFail) {
            fail("See above");
        }
    }

    private static boolean hasNoEnclosingClass(Class<?> clazz) {
        return clazz.getEnclosingClass() == null || hasNoEnclosingClass(clazz.getSuperclass());
    }

    @Test
    public void testBukkitMap() {
        List<Class<?>> classes;
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().whitelistPackages("net.minecraft.world.entity").scan()) {
            classes = scanResult.getSubclasses("net.minecraft.world.entity.Mob").loadClasses();
        }
        assertNotEquals(Collections.emptyList(), classes, "There are supposed to be more than 0 entity types!");

        boolean shouldFail = false;
        for (Class<?> nmsClass : classes) {
            Class<? extends Mob> bukkitClass = MobGoalHelper.toBukkitClass((Class<? extends net.minecraft.world.entity.Mob>) nmsClass);
            if (bukkitClass == null) {
                shouldFail = true;
                System.out.println("Missing bukkitMap.put(" + nmsClass.getSimpleName() + ".class, " + nmsClass.getSimpleName().replace("Entity", "") + ".class);");
            }
        }

        if (shouldFail) {
            fail("See above");
        }
    }
}
