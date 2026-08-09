# AyCore 1.3.2-BETA minimal cache optimization patch

This package contains source only for the two replaced classes. It is not a full decompilation of AyCore.

## Scope

- `ReflectionUtil`: caches resolved reflection methods by declaring class and signature.
- `INMSClass`: caches stable NMS classes, constructors, methods and public fields.

No configuration, authentication, command, database, network, task, GUI or business behavior is changed.
No player, entity, connection, packet instance, configuration value or query result is cached.

## Build

Requires JDK 17+ with `javac --release 8`, Python 3 and `zip`.

```bash
cp /path/to/AyCore-1.3.2-BETA.jar lib/
./build.sh
```

The generated replacement classes use Java 8 bytecode (major version 52).
