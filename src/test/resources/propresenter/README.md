# Sample files

Fixtures for the converter's own tests, kept because the formats they cover cannot be generated
by hand: a ProPresenter 7 document is protocol buffers and an EasyWorship schedule is a fixed-layout
binary, and a hand-written approximation of either would pass the tests while failing on the real
thing.

| Directory | Files | Source | Licence |
|---|---|---|---|
| `propresenter/` | `.pro4`, `.pro5`, `.pro6`, `.pro` | [ChrisMBarr/ProPresenter-Parser](https://github.com/ChrisMBarr/ProPresenter-Parser) `sample-files/` | MIT |
| `easyworship/` | `.ews` | [meinders/lithium-ews](https://github.com/meinders/lithium-ews) `src/test/resources` | GPL-3.0 |

Both are compatible with this project's GPL-3.0 licence. The EasyWorship SQLite flavours are not
here because they are ordinary SQLite and the tests build them with the driver the converter reads
them with, which is a better fixture than a copied file.
