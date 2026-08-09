# Own-JAR default configuration loading

The bundled default configuration is stored at the unique non-YAML path:

```text
META-INF/mintcontrol/defaults.rc17
```

`PluginConfig` opens the exact JAR or classes directory reported by the main class ProtectionDomain and copies only that exact entry. It does not call `JavaPlugin#getResource`, `ClassLoader#getResource`, `saveResource` or `saveDefaultConfig`.

The output file remains:

```text
plugins/MintControl/config.yml
```
