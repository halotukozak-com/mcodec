# What mcodec needs from Made

Running notes on Made's API as mcodec exercises it. Updated as phases progress.

## Made 0.1.1

- **Field label is a type member, not a value.** Read it with `compiletime.constValue[elem.Label]`, not `elem.label`. `default` is the only runtime value member.
- **`Made.derived[T]` must stay `transparent inline` through any seam.** Returning a widened `Made.Of[T]` erases the `Elems` refinement, so element metadata reads as `Any`. Relevant for Phase 4 derivation.
- **Published POM pins `scala3-library_3:3.8.4-RC3`** — forces mcodec onto that RC. RC3 cleanly consumes the LTS-built munit/scalacheck (no TASTy mismatch, no eviction).

## Decisions

- No adapter layer over Made: derivation code uses `made.*` directly (the earlier "single import site" isolation was dropped). Made churn is accepted as it surfaces here.
