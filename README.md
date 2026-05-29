# NetBeans C/C++ Plugin

Apache NetBeans plugin providing C and C++ language support.

## Overview

This repository contains the C/C++ modules extracted from the [apache/netbeans](https://github.com/apache/netbeans)
monorepo, repackaged as a standalone Maven project using `nbm-maven-plugin`.

## Modules

### `cnd/` — C/C++ Language Support (26 modules)

| Module | Description |
|---|---|
| `cnd` | Core: model, indexing, XML API |
| `cnd.api.project` | C/C++ project API |
| `cnd.api.remote` | Remote access API |
| `cnd.editor` | C/C++ editor |
| `cnd.lexer` | C/C++ lexer |
| `cnd.makeproject` | Make project support |
| `cnd.toolchain` | Toolchain support (gcc, clang, MSVC) |
| `cnd.debugger.gdb2` | GDB debugger integration |
| `cnd.lsp` | Language Server Protocol integration |
| `cnd.meson` | Meson build system support |
| ... | (see `cnd/pom.xml` for full list) |

### `dlight/` — Remote Execution Infrastructure (5 modules)

Required by CND for remote compilation and debugging over SSH.

| Module | Description |
|---|---|
| `dlight.libs.common` | File utilities |
| `dlight.remote` | Remote filesystem API |
| `dlight.remote.impl` | SFTP/SSH implementation |
| `dlight.remote.ui` | SSH connection UI |
| `dlight.sendto` | Send to remote terminal |

## Building

```bash
mvn clean verify
```

## Requirements

- Java 11+
- Maven 3.8+
- Apache NetBeans Platform (fetched from Maven Central)

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE)
