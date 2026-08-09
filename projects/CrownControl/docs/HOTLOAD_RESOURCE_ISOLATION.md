# Hot-load resource isolation

Older builds appended the complete CrownControl JAR to Forge `LaunchClassLoader`, and rc7 still mounted the complete JAR on a private `URLClassLoader`. Although rc7 avoided global URL injection, that loader still had access to `plugin.yml` and the bundled default configuration.

CrownControl 1.0.0-rc8 uses a resource-free bytecode bridge:

- the plugin JAR is never appended to Forge `LaunchClassLoader`;
- the runtime bridge loader has no URL and does not extend `URLClassLoader`;
- only class bytes below `cn/licry/crowncontrol/runtime/` are read from CrownControl's exact code source;
- those bytes are defined from an in-memory map;
- plugin resources are not mounted on the runtime loader;
- unloading clears the plugin, service, logger and reflected method references even if Forge listener removal fails.

A complete JVM shutdown and cold start is mandatory when replacing rc7 or any older build. A JAR URL previously added to a long-lived classloader cannot be removed safely by PlugMan or `/reload`.
