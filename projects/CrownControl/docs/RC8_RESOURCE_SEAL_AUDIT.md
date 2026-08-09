# CrownControl rc8 resource sealing audit

- The full plugin JAR is never appended to Forge LaunchClassLoader.
- The runtime bridge does not extend URLClassLoader and holds no URL.
- Only `cn.licry.crowncontrol.runtime.*` class bytes are copied from CrownControl's exact code source.
- The runtime loader defines classes from an in-memory byte map and exposes no plugin resources.
- Bundled defaults use `META-INF/crowncontrol/defaults.rc8`, not a generic YAML filename.
- `PluginConfig` reads the default directly from CrownControl's own JAR with `JarFile`.
- Unregister clears all references to the plugin/config/service graph; a stale listener is inert.
- A full JVM cold restart is required when replacing rc7 or older.
