package io.papermc.paper.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ItemStackRepairCheckTest extends AbstractTestingBase {

    @Test
    public void testIsRepariableBy() {
        ItemStack diamondPick = new ItemStack(Material.DIAMOND_PICKAXE);

        assertTrue(diamondPick.isRepairableBy(new ItemStack(Material.DIAMOND)), "diamond pick isn't repairable by a diamond");
    }

    @Test
    public void testCanRepair() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        assertTrue(diamond.canRepair(new ItemStack(Material.DIAMOND_AXE)), "diamond can't repair a diamond axe");
    }

    @Test
    public void testIsNotRepairableBy() {
        ItemStack notDiamondPick = new ItemStack(Material.ACACIA_SAPLING);

        assertFalse(notDiamondPick.isRepairableBy(new ItemStack(Material.DIAMOND)), "acacia sapling is repairable by a diamond");
    }

    @Test
    public void testCanNotRepair() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        assertFalse(diamond.canRepair(new ItemStack(Material.OAK_BUTTON)), "diamond can repair oak button");
    }

    @Test
    public void testInvalidItem() {
        ItemStack badItemStack = new ItemStack(Material.ACACIA_WALL_SIGN);

        assertFalse(badItemStack.isRepairableBy(new ItemStack(Material.DIAMOND)), "acacia wall sign is repairable by diamond");
    }
}
