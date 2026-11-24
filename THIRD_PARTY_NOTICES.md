# Third-party notices

## Sable & Flowing Fluids Compat

MSpell Compatibility Suite is inspired by and partially adapts the described compatibility approach and inlet catch-point detection from Sable & Flowing Fluids Compat 1.0.2 by Gideon Strom (FluffyCommando).

- Project page: https://www.curseforge.com/minecraft/mc-mods/sable-flowing-fluids-compat
- Source code: https://github.com/FluffyCommando/Sable-Flowing-Fluids-Compat
- License: MIT

The applicable upstream MIT notice is reproduced below. The MSpell Compatibility Suite project is independent and is not affiliated with or endorsed by the upstream author.

```text
MIT License

Copyright (c) 2026 Gideon Strom

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Runtime dependencies are downloaded from Modrinth during the development build and are not bundled in the MSpell Compatibility Suite JAR. Their licenses remain with their respective authors.

## Optional Tree Physics integration

MSpell Compatibility Suite calls Tree Physics 2.4's public `FloodFillUtil.blockBroken` API when tree-log blocks are affected by explosions or removed by server-side block destruction. Tree validation, tags, configuration, Sable sub-level assembly, and falling physics remain implemented by Tree Physics. No Tree Physics source code or binary is copied into this project; Tree Physics remains a separate optional runtime dependency.

- Project page: https://modrinth.com/mod/tree-physics
- Source code: https://github.com/Monad-Modding/tree-physics

## Optional Sable: Destructive compatibility

The integration also observes server-side block replacement, allowing Sable: Destructive's world-damage path to trigger Tree Physics when it removes tagged tree logs. No Sable: Destructive source code is copied into this project.

- Project page: https://modrinth.com/mod/sable-destructive
