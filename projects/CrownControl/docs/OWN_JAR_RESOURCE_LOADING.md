# Own-JAR resource loading

The default configuration is stored under the unique, non-YAML resource path:

```text
META-INF/crowncontrol/defaults.rc8
```

`PluginConfig` opens the exact JAR or classes directory reported by the CrownControl main class code source and copies that entry directly. It does not call `JavaPlugin#getResource`, `ClassLoader#getResource`, or a parent/global resource search. Runtime plugin name and main-class identity are checked before configuration I/O.
