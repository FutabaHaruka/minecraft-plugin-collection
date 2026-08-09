# Hot-load resource isolation

MintControl 1.0.0-rc17 never appends its JAR to Forge LaunchClassLoader and no longer mounts the JAR on a private URLClassLoader.

The plugin opens its exact code source only long enough to copy bytecode for `cn.licry.mintcontrol.runtime.*` into memory. Those classes are defined by a resource-free `ClassLoader` whose parent is the Forge launch loader. The runtime loader has no URL and cannot return MintControl's `plugin.yml`, defaults or other resources.

On disable, the Forge hook is unregistered, callback references are cleared, the command executor/completer is detached and the runtime class byte map is erased. A stale listener left behind by a broken hot loader is inert.

A full JVM restart remains mandatory when upgrading from versions that injected a URL into LaunchClassLoader.
