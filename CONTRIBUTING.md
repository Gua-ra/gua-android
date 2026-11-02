# Contributing to Gua for Android

Gua for Android is a fork of [Element X Android](https://github.com/element-hq/element-x-android). Upstream's developer documentation under [docs/](docs/) still describes the codebase. This guide covers what is specific to Gua.

Upstream's CLA, Localazy and pull request instructions do not apply here. There is no CLA, and pull requests go to `Gua-ra/gua-android`.

## Where the Gua code lives

- `libraries/guaresolver`: the resolver client that picks the homeserver before sign-in.
- `features/findfriends`: Find Friends.
- Gua screens and settings sit next to the upstream feature modules they extend.
- Fork strings use `gua_*` keys: base English in each module's `values/temporary.xml`, translations in `values-<locale>/gua_translations.xml`. Upstream strings come from upstream; do not edit `translations.xml` and do not reuse an upstream key.

Keep Gua changes in these places where you can. It keeps upstream merges small.

## Setup

Build and point the app at your own stack as described in the README's [Building](README.md#building) section.

## Before opening a pull request

```bash
./tools/scripts/check-constraints.sh   # forbidden identifiers, AI attribution, committed secrets
./tools/quality/check.sh               # detekt, ktlint, lint, konsist, markdown TOCs
./gradlew test
```

- A changed screen needs its screenshot goldens re-recorded: `./gradlew :tests:uitests:recordPaparazziDebug --tests '*<PreviewName>*'`. Goldens are stored in git LFS. Local renders can differ from CI; when CI fails on a screen you changed, take the goldens from the `gua-test-failures` artifact of that run.
- Adding or removing a preview re-shards the screenshot tests and churns goldens for unrelated screens. Cover a new state with a presenter or view test unless the preview is needed.
- Composables keep upstream's convention: a preview function annotated with `@PreviewsDayNight`, named `<Composable>Preview`, with `ElementPreview` as its root.
- License headers: a new Gua file starts with `Copyright <year> Gua` followed by the repository's SPDX line. A modified upstream file keeps its Element notice. The konsist test accepts only these two shapes.

## Pull requests

Branch from `develop`. Commits, pull request text and writing follow the [org contribution guide](https://github.com/Gua-ra/.github/blob/main/CONTRIBUTING.md#pull-requests).

## Reporting problems

- Bugs and requests: [GitHub issues](https://github.com/Gua-ra/gua-android/issues).
- Security problems: [SECURITY.md](SECURITY.md), never a public issue.

## Upstream

See the README's [Upstream relationship](README.md#upstream-relationship).
