# Changelog

## [0.3.1](https://github.com/Marchland/conduit/compare/v0.3.0...v0.3.1) (2026-10-06)


### Miscellaneous Chores

* move packages to Marchland and rename mf24j -&gt; microformats2 ([fb31523](https://github.com/Marchland/conduit/commit/fb315236c40bd0f29e9b7826c7f5c9319d84c8de))
* move packages to Marchland and rename mf24j -&gt; microformats2 ([ce07e4a](https://github.com/Marchland/conduit/commit/ce07e4a7b5dd715ff9f29bc99cbbe815bdafbd4d))


### Build System

* bump content-client to 2.0.2 ([63dccb1](https://github.com/Marchland/conduit/commit/63dccb123b4d9777bf2e1247b4fed7a1a5bda2c0))
* bump microformats2 to 0.1.2 ([d7b7dbf](https://github.com/Marchland/conduit/commit/d7b7dbfc2642594b0b79e08e4cfa9d202d6852d2))

## [0.3.0](https://github.com/jacobsandersen/conduit/compare/v0.2.0...v0.3.0) (2026-10-06)


### Features

* configurable JetStream stream replicas ([1c6f9b4](https://github.com/jacobsandersen/conduit/commit/1c6f9b482e70c6297a50925da85ffbc8b4ab778e))
* make JetStream stream replicas configurable ([a17c5cb](https://github.com/jacobsandersen/conduit/commit/a17c5cb0b63a544adde3c088125c777a4326c6e7))


### Tests

* cover the reconciliation sweep ([299554c](https://github.com/jacobsandersen/conduit/commit/299554c6677e961cf8e2440ede59c2f50629da65))
* cover the reconciliation sweep ([e088239](https://github.com/jacobsandersen/conduit/commit/e088239bddcad3d71f94d90ce172a464ada922bb))

## [0.2.0](https://github.com/jacobsandersen/conduit/compare/v0.1.0...v0.2.0) (2026-10-06)


### Features

* add the reconciliation sweep ([3c4cdcd](https://github.com/jacobsandersen/conduit/commit/3c4cdcd2413173d66288dff54ee2e8db6741f34f))
* carry the target name on syndication events for the read model ([afdaaa9](https://github.com/jacobsandersen/conduit/commit/afdaaa9eb511a1554b2be199e8313c64198b4010))
* Conduit syndication + WebSub service ([9cae8f4](https://github.com/jacobsandersen/conduit/commit/9cae8f4bcab867212736ff675991fc127fc1b9d0))
* conduit WebSub + syndication scaffold ([bfa96c0](https://github.com/jacobsandersen/conduit/commit/bfa96c0a1e640a6d7855e3f74d0aef01513dabfc))
* extract syndication + WebSub into a multi-module Conduit service ([894cc83](https://github.com/jacobsandersen/conduit/commit/894cc8366740edae1e08764df77311fa6cb71ed2))


### Code Refactoring

* own the SYNDICATION stream (stream per producer) ([999e7b3](https://github.com/jacobsandersen/conduit/commit/999e7b3a2d004dc5845c0e6bff071b8f4065207b))


### Continuous Integration

* add a workflow to publish conduit-client to GitHub Packages ([99af209](https://github.com/jacobsandersen/conduit/commit/99af2095de1662bc6794d7b8fbd0bec5bc219dbd))
* add lint and test workflow ([be330d1](https://github.com/jacobsandersen/conduit/commit/be330d11f6563f4acbc45ed7e64fa38972d9ac3c))
* add release-please and a distroless image pipeline ([184546d](https://github.com/jacobsandersen/conduit/commit/184546d65c953b47c69539d719f3843bdfcfb09a))
* authenticate package reads with a dedicated PACKAGES_TOKEN ([70b1225](https://github.com/jacobsandersen/conduit/commit/70b1225679b2aaa782fddf68b01f697c82be0a7a))
* read private content-client via a packages token ([126b1d6](https://github.com/jacobsandersen/conduit/commit/126b1d6533a8aa27d8b76a5687332fdf0ce78acf))
* release-please + distroless image pipeline ([41a856b](https://github.com/jacobsandersen/conduit/commit/41a856bd0463a21733ac1634bc9bb36de0b41e05))
