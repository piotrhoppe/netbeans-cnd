# NetBeans C/C++ Plugin

Apache NetBeans plugin providing C and C++ language support.

## Overview

This repository contains the C/C++ modules extracted from the [apache/netbeans](https://github.com/apache/netbeans)
monorepo, repackaged as a standalone Maven project using `nbm-maven-plugin`.

## Modules

### `cnd/` — C/C++ Language Support (27 modules)

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
| `cnd.discovery` | Project creation from existing sources (see [module README](cnd/cnd.discovery/README.md)) |
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
export JAVA_HOME=/path/to/jdk-17
mvn clean install -Dmaven.test.skip=true
```

NBM files are collected in `target/nbms/`.

To generate an Update Center site (for NetBeans Plugin Manager):

```bash
mvn clean install -Pupdate-center -Dmaven.test.skip=true
```

Output: `target/update-center/netbeans_site/updates.xml`

## Installation

Install all NBM files to your NetBeans `cnd` cluster:

```bash
NB_HOME=~/path/to/netbeans
for nbm in target/nbms/*.nbm; do
  unzip -o "$nbm" "netbeans/*" -d /tmp/nbm_install
  cp -r /tmp/nbm_install/netbeans/* "$NB_HOME/cnd/"
  rm -rf /tmp/nbm_install
done
```

### Patching nativeexecution (required)

The `cnd.lsp` module uses the `nativeexecution` API but is not in its
`OpenIDE-Module-Friends` list in standard NetBeans distributions.
You must patch `nativeexecution` once per NB installation:

```bash
./scripts/patch-nativeexecution.sh ~/path/to/netbeans
```

Without this patch, running C/C++ programs will fail with
`NoClassDefFoundError: ExecutionEnvironment`.

## Requirements

- Java 17+
- Maven 3.8+
- Apache NetBeans 14+ (platform dependencies fetched from Maven Central)

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE)
