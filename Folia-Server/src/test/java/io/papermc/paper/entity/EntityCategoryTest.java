package io.papermc.paper.entity;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraft.world.entity.MobType;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.EntityCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityCategoryTest {

    @Test
    public void test() throws IllegalAccessException {

        Map<MobType, String> enumMonsterTypeFieldMap = Maps.newHashMap();
        for (Field field : MobType.class.getDeclaredFields()) {
            if (field.getType() == MobType.class) {
                enumMonsterTypeFieldMap.put( (MobType) field.get(null), field.getName());
            }
        }

        for (EntityCategory entityCategory : EntityCategory.values()) {
            enumMonsterTypeFieldMap.remove(CraftLivingEntity.fromBukkitEntityCategory(entityCategory));
        }
        assertTrue(enumMonsterTypeFieldMap.size() == 0, MobType.class.getName() + " instance(s): " + Joiner.on(", ").join(enumMonsterTypeFieldMap.values()) + " do not have bukkit equivalents");
    }
}
