# CrownControl strict folder isolation

Version: 1.0.0-rc5

The plugin is allowed to read and write only under `plugins/CrownControl/`.

Enforced conditions:

- Bukkit `getDataFolder()` basename must be exactly `CrownControl`.
- The assigned path must equal `<plugins-parent>/CrownControl` after absolute normalization.
- `.plugin-owner` must contain `CrownControl` and `cn.licry.crowncontrol.CrownControlPlugin`.
- Absolute paths, `.` and `..` segments are rejected.
- Symlinks are rejected for the plugin directory, config, logs and temporary files.
- Configuration bootstrap failure is fatal; the plugin does not continue in diagnostic mode.
- `config.yml`, logs, backups and temporary files are all created inside the exclusive folder.
