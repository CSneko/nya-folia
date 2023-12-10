package io.papermc.paper.plugin.bootstrap;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the context provided to a {@link PluginBootstrap} during both the bootstrapping and plugin
 * instantiation logic.
 * A boostrap context may be used to access data or logic usually provided to {@link org.bukkit.plugin.Plugin} instances
 * like the plugin's configuration or logger during the plugins bootstrap.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface BootstrapContext extends PluginProviderContext {
}
