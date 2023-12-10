package io.papermc.paper.command;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommandBlockHolder {

    /**
     * Gets the command that this CommandBlock will run when powered.
     * This will never return null.  If the CommandBlock does not have a
     * command, an empty String will be returned instead.
     *
     * @return Command that this CommandBlock will run when activated.
     */
    @NotNull
    String getCommand();

    /**
     * Sets the command that this CommandBlock will run when powered.
     * Setting the command to null is the same as setting it to an empty
     * String.
     *
     * @param command Command that this CommandBlock will run when activated.
     */
    void setCommand(@Nullable String command);

    /**
     * Gets the last output from this command block.
     *
     * @return the last output
     */
    @NotNull
    Component lastOutput();

    /**
     * Sets the last output from this command block.
     *
     * @param lastOutput the last output
     */
    void lastOutput(@Nullable Component lastOutput);

    /**
     * Gets the success count from this command block.
     * @see <a href="https://minecraft.wiki/wiki/Command_Block#Success_count">Command_Block#Success_count</a>
     *
     * @return the success count
     */
    int getSuccessCount();

    /**
     * Sets the success count from this command block.
     * @see <a href="https://minecraft.wiki/wiki/Command_Block#Success_count">Command_Block#Success_count</a>
     *
     * @param successCount the success count
     */
    void setSuccessCount(int successCount);
}
