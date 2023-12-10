package io.papermc.paper.entity;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ScanResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.fail;

public class EntitySetItemSlotSilentOverrideTest extends AbstractTestingBase {

    public static Stream<ClassInfo> parameters() {
        final List<ClassInfo> classInfo = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph()
            .enableClassInfo()
            .enableMethodInfo()
            .whitelistPackages("net.minecraft")
            .scan()
        ) {
            for (final ClassInfo subclass : scanResult.getSubclasses("net.minecraft.world.entity.LivingEntity")) {
                final MethodInfoList setItemSlot = subclass.getDeclaredMethodInfo("setItemSlot");
                if (!setItemSlot.isEmpty()) {
                    classInfo.add(subclass);
                }
            }
        }
        return classInfo.stream();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void checkSetItemSlotSilentOverrides(ClassInfo overridesSetItemSlot) {
        final MethodInfoList setItemSlot = overridesSetItemSlot.getDeclaredMethodInfo("setItemSlot");
        for (final MethodInfo methodInfo : setItemSlot) {
            for (final MethodParameterInfo methodParameterInfo : methodInfo.getParameterInfo()) {
                if ("boolean".equals(methodParameterInfo.getTypeDescriptor().toStringWithSimpleNames())) {
                    return;
                }
            }
        }
        fail(overridesSetItemSlot.getName() + " needs to override setItemSlot with the boolean silent parameter as well");
    }
}
