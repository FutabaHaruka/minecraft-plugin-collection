# MintControl strict folder isolation

Version: 1.0.0-rc14

The plugin is allowed to read and write only under `plugins/MintControl/`.

Enforced conditions:

- Bukkit `getDataFolder()` basename must be exactly `MintControl`.
- The assigned path must equal `<plugins-parent>/MintControl` after absolute normalization.
- `.plugin-owner` must contain `MintControl` and `cn.licry.mintcontrol.MintControlPlugin`.
- Absolute paths, `.` and `..` segments are rejected.
- Symlinks are rejected for the plugin directory, config, logs and temporary files.
- Configuration bootstrap failure is fatal; the plugin does not continue in diagnostic mode.
- `config.yml`, logs, backups and temporary files are all created inside the exclusive folder.
