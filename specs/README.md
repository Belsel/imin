# Specs

Each unit of work gets a folder: `specs/<slug>/spec.md`, started from
`specs/_TEMPLATE.md`. The Leader agent tracks each spec's lifecycle via the
`status` field in its frontmatter:

```
draft -> approved -> in-progress -> implemented -> verified
```

- **draft** — written by `analyst`, not yet reviewed.
- **approved** — requirements and acceptance criteria signed off (by user or
  Leader); ready for design/implementation.
- **in-progress** — `architect` and/or `implementer` actively working it.
- **implemented** — code written, awaiting/under `reviewer` and `tester`.
- **verified** — `reviewer` has signed off and `tester` has confirmed every
  acceptance criterion actually passes.

Keep one spec per coherent unit of work. If a request bundles unrelated
requirements, split it into separate spec folders rather than growing one
spec sideways.
