# Next release plan: parallel farm growth and compact timer cards

Status: implementation in progress.
Prepared: 2026-11-03.
Suggested release: 0.3.0, versionCode 6 (current checkout: 0.2.1, versionCode 5).

## 1. Outcomes and scope

1. Each unlocked field receives the full value of every newly eligible nutrient source.
2. Empty fields and fields with ripe crops keep accumulating their own nutrients.
3. Existing installations rebuild farm progression under the new rules without changing tasks,
   habits, time records, running timers, goals, reminders, settings or statistics.
4. Timer cards use substantially less space on the target 1216 x 2640 / 480 dpi phone, approximately
   405 dp wide, while preserving readable text and usable controls at 1.0x and 1.3x font scales.

This release does not change crop requirements, nutrient weights, crop artwork, timer settlement,
or the existing 3 -> 4 -> 5 field unlock thresholds. Balance changes should follow actual usage of
parallel growth rather than being bundled into the migration.

## 2. Findings in the current checkout

- `domain/.../farm/FarmInteractor.kt`, `reconcile`: each new source is assigned to the first
  unfinished plot sorted by slot. Remaining sources become one shared compost pool. Existing
  assignments survive recalculation; invoking `sync()` alone cannot switch to parallel growth.
- `data/.../db/entity/FarmEntities.kt`: `farm_nutrient.sourceKey` is the primary key and each entry
  has only one `plotId`. Writing several entries with the same key would overwrite allocations.
- Farm progress includes completed time records, completed tasks, completed habit check-ins and
  reached goals. Running timers are not awarded until settled. Manual time receives half credit;
  a single record is capped at 240 points. Preserve these rules.
- Historical planting and harvest rows exist, but there is no complete log of compost applications,
  field unlock events or past edits. Source records alone cannot reproduce historical crop choices
  and harvest actions under a different allocation rule.
- `feature/tracker/.../TrackerScreen.kt`: the grid minimum is the larger of
  `160.dp * fontScale` and the measured width of `9999:59:59` plus padding. At 1.3x the first value
  alone is 208 dp; two columns cannot fit inside 405 dp minus 32 dp gutters and the 6 dp gap.
  The extreme duration sample can also force a single column at smaller font scales.
- Each timer card also uses a 48 dp icon row, a large duration, two reserved subtitle lines and a
  full-width bottom button. Multiple running timers add a separate hero that duplicates card data.
- Room schema is version 1. Backup JSON is format 1, and decoding currently rejects other formats.
- `BackupStore.restore()` currently clears tables before its insertion transaction. Restore must
  become atomic as part of this release's backup work; its current ordering is not a safe rollback
  boundary for the new migration.

## 3. Proposed farm rules

### 3.1 Independent field balances

Every eligible source awards its full points to **every field already unlocked when that source is
first reconciled**. Points are neither divided between fields nor routed to a selected crop.

For a field:

```text
earned = sum(current points of its eligible sources)
spent = sum(requirements consumed by its harvests in the new farm generation)
available = max(0, earned - spent)
crop growth = min(crop requirement, available) when planted
stored surplus = available - crop growth when planted; otherwise available
```

- Example: three unlocked fields, each growing a carrot, and one 60-minute timed record produce
  60 points in each field. All three carrots reach 60 / 180. The focus statistic remains 60 minutes.
- After another 120 eligible points, all three carrots are ripe. Harvesting one consumes exactly
  180 points from that field only; the other two remain ripe.
- Example: a field with 250 available points grows a carrot to 180 / 180 and stores 70 points.
  Harvest consumes 180. Replanting a carrot immediately shows 70 / 180.
- Empty fields accumulate the same points. Planting automatically uses that field's available
  balance. Remove the shared compost pool, manual fertilization and the planting compost checkbox.
- Ripe crops continue to store surplus locally. No nutrients are lost to overshoot, and nutrients
  cannot be transferred to another field.
- Harvest remains an explicit user action. No automatic harvesting or replanting.
- The fourth and fifth fields unlock after 5 and 15 new-generation harvests. They begin at zero and
  receive subsequent sources; historical points do not suddenly multiply when a field unlocks.
- Keep crop unlock criteria. Harvest-based and collection-based criteria use new-generation
  harvests; focus and habit streak criteria use the preserved source data.

### 3.2 Editing, backdating and settlement

- A stable source key is reconciled once and updated thereafter. Repeated sync, process restart or
  reopening the farm must never create another award for the same source.
- Record edits, deletions, task undo and check-in undo update that source's contribution to every
  eligible field. A source's eligibility does not change when its timestamp or amount changes.
- Eligibility follows first reconciliation order, not a mutable event timestamp. A newly added
  backdated record benefits the fields currently unlocked. Editing a pre-unlock source does not
  make it eligible for a later field. Explain this rule in the farm help text.
- Retain source identity/first-seen order after removal with a zero-value tombstone. Undoing and
  restoring the same source must not bypass a later field's eligibility cutoff.
- Completed harvests and earned badges remain permanent after normal source edits. If deleting
  sources makes earned points fall below points already spent, keep the internal deficit: future
  points repay it before new growth. Clamp only the visible available balance, not the ledger.
- Unlocks are permanent within the current farm generation. A source deletion must not relock an
  occupied field. Migration is the one explicit generation reset.
- Retain current settlement timing: no persistent nutrient grants from an ongoing timer. A timer
  crossing an upgrade continues running and awards its completed record once after settlement.
- Serialize reconciliation, planting, harvesting, unlocking and migration through one coordinator
  and transactions. Reconcile pending sources before harvest validation and before establishing a
  newly unlocked field's cutoff; the source that preceded unlock must not be awarded again.
- Use `Long` for cumulative points, sequence values and spent totals. Preserve current rounding
  and per-record cap; do not multiply real focus statistics by the field count.

## 4. Existing-user migration

### 4.1 Recommended reset policy

Adopt the requested reset for **farm progression**, and keep an immutable local snapshot of the
old farm so the user's previous collection is not irretrievably lost.

| Data | Upgrade treatment |
| --- | --- |
| Tasks, habits, check-ins, records, goals, running timers, other app data | Preserve IDs and values |
| Old plots, nutrient assignments, harvest log, farm achievements | Archive as the legacy farm; exclude from new progression |
| New active farm | Start with three empty fields and no harvests |
| Historical nutrient sources still present | Recalculate with current weights, and credit the full sum to each of the three base fields |
| Focus/task/habit/goal achievements and crop unlocks | Re-evaluate from preserved sources; record recalculation time where a historical unlock time is unavailable |
| Harvest/collection achievements and harvest-dependent unlocks | Restart from zero in the active farm; old results remain in the legacy archive |
| Fourth and fifth fields | Unlock again from new-generation harvests, with zero initial balance |

This is a rebuild of historical **earning entitlement**, not an invented replay of planting and
harvesting. A user may plant and manually harvest immediately if the recalculated balance covers
the selected crop. Previously spent nutrients are not subtracted again because the corresponding
harvests belong exclusively to the archived farm. State this compensation explicitly.

Do not auto-choose crops or synthesize past harvest timestamps. If exact preservation of old crop
choices becomes a requirement, revise this migration policy before implementation.

### 4.2 Transaction and recovery

1. Migrate the schema non-destructively from Room 1 to 2; retain all existing v1 tables/data until
   the farm rebuild completes. Never use destructive migration fallback.
2. On database initialization, inspect a Room-backed farm rules version. Before any farm mutation,
   take a consistent legacy farm snapshot and source snapshot inside the coordinated transaction.
3. Create the legacy archive, canonical source ledger, base-field states and new-generation farm
   state. Seed both new installs and migrated installs with the same rules.
4. Validate eligible totals, no duplicate active slot, and preservation of all unrelated data.
5. Commit the farm rules version and the rebuilt state together. Do not use a separate DataStore
   boolean as the sole completion marker.
6. Expose a one-time migration summary in both languages: farm progression rebuilt, source history
   preserved, recalculated points per base field, and access to the legacy farm summary.

Migration must be idempotent. A crash before commit rolls back and retries; a crash after commit
must not reset the new farm again. Users with no prior farm, no sources, all fields unlocked, or a
running timer must pass through this same path safely. This is a local upgrade on each device;
the app has no account service through which to reset installations remotely.

### 4.3 Persistence proposal

Keep v1 schema snapshots unchanged and add a reviewed v2 schema. Prefer one canonical source row
over copying every source into three to five mutable allocation rows.

- `farm_state`: rules version, farm generation and next source sequence.
- `farm_source`: stable source key, point amount, earned time and immutable first-seen sequence;
  removed sources retain zero-valued tombstones. Preserve deterministic ordering within a batch.
- `farm_slot`: slot index and eligibility cutoff sequence. The base three include all sources;
  newly unlocked fields include only sources first seen after their cutoff.
- New-generation plots and harvests retain slot/crop identity and exact spent amounts. Existing
  tables may be reused after their contents have been archived transactionally.
- Legacy farm archive: versioned immutable payload containing original plots, ledger, harvests and
  badges. Include a small read-only legacy summary in the UI; archive data never drives balances.
- Compute spent totals from new-generation harvest rows instead of maintaining an independent
  mutable spent counter. Add suitable indices after reviewing query patterns.

The schema names above are proposed implementation names. Repository APIs, domain models, fake
repositories and Room mappings must change together. Screens continue to call interactors.

### 4.4 Backup and restore

- Write backup format 2 with required format validation and explicit farm rules version, sources,
  cutoffs, sequence state, plots, harvests, badges and legacy archive. Versioned eligibility state
  is essential; restoring only current points cannot recover field balances faithfully.
- Read formats 1 and 2. Format 1 restores source data and runs the same legacy-to-new rebuild,
  regardless of whether the current installation has already migrated.
- Format 2 restores the new farm exactly, including zero-value source identities and field cutoffs;
  do not re-award historical nutrients or reset progress. Validate relationships before mutation.
- Older app versions cannot read format 2. State that explicitly in settings and release notes.
- Make clearing and inserting Room rows part of one rollback-capable restore transaction. Keep
  preferences application and reminder/timer resynchronization as retryable post-commit work.
- Reset/reload any in-memory farm migration cache after restore. Serialize restore against farm
  writes, and check the restored rules version rather than a device-global migration flag.
- Keep the required format field, reject unsupported future formats before clearing, preserve
  unknown-key tolerance, and test backup round trips for both active and archived farm data.

## 5. Timer card redesign

### 5.1 Layout direction

Use a compact two-column grid for ordinary content at 405 dp / 1.0x. With existing 16 dp page
gutters and a proposed 8 dp gap, each card is approximately 182.5 dp wide.

- Header: a smaller decorative icon, activity name and overflow menu. Avoid a standalone large
  icon row; allow long names to wrap without ellipsis.
- Body: use a smaller tabular duration style. Break exceptionally long hours/minutes/seconds into
  measured lines or use a wider row layout; do not size every card from a 9999-hour sample.
- Footer: compact today/goal information and one start/stop control. Use the same control bounds
  in idle and running states and reserve space for the changing duration and subtitle content.
- Initial prototype height budget: 164-184 dp for short names and ordinary content at 1.0x.
  This is an acceptance target to measure on device, not a hard maximum for large or long text.
- Use at least 48 dp interactive targets for start/stop and overflow. Reduce decorative size,
  whitespace and redundant lines rather than shrinking hit areas or scaling geometry with fonts.
- For multiple running timers, replace the large duplicate hero with a concise full-width summary
  strip containing the count and Stop all. Individual controls stay on their activity cards.

### 5.2 Adaptive rules

- Extract a pure layout policy from available width, measured text requirements and content.
  Test two columns at the target width with ordinary text; fall back to compact full-width rows
  when two readable cards cannot fit, including at 1.3x where necessary.
- Font scale changes text measurements and reflow only. Remove multiplication of card minima,
  padding, cells and touch targets by `fontScale`, including in the running summary.
- Add reusable card padding, gap, icon, control and row metrics to `MeoLayout`, with compact and
  regular values. Do not scatter new fixed dimensions through `TrackerScreen.kt`.
- Idle and running variants of the same card must have identical measured height. Reserve the
  maximum of the relevant idle/running text layouts, including linked task/comment content.
- No `animateContentSize`, state-dependent padding, clipped labels or ellipsized names. Preserve
  natural wrapping, accessible control labels, goal details and all existing overflow actions.
- Capture the current screen first. Compare actual card bounds and visible card count under the
  same content/device/font configuration; do not claim a measured improvement before doing so.

## 6. Implementation order and deliverables

| Step | Work | Completion evidence |
| --- | --- | --- |
| 1. Rules and baseline | Lock rules/examples in this proposal; capture old timer bounds; add pure farm balance and layout policies | Deterministic domain/layout cases and comparable baseline screenshots |
| 2. Storage and farm engine | Add Room 1 -> 2 migration, canonical ledger/cutoffs, balances, harvest transactions and legacy archive | Domain tests, source deletion/undo cases, schema migration test |
| 3. Upgrade and backup | Add gated once-only rebuild, restore formats 1/2, atomic Room restore, migration summary and archive view | Upgrade, interrupted retry and restore round-trip instrumentation tests |
| 4. UI | Update planting/balance/help text, remove shared compost UI, build compact timer cards and running summary | English/Chinese, light/dark, 1.0x/1.3x device checks |
| 5. Release qualification | Run regression checks, review balance impact, update compatibility docs and version metadata | Verified build, test/lint results, device evidence and release notes |

Farm and UI changes ship together only after upgrade/restore checks pass. No release publishing,
version bump or production data change is part of this planning task.

## 7. Acceptance checks

### Farm and migration

- One 60-point source awards exactly 60 to each eligible field, whether planted, empty or ripe.
- All crop requirements, manual-record rounding, 240-point cap and six growth stages stay intact.
- Overflow stays in its own field; harvesting/replanting one field never alters another.
- Reconciliation, process restart and repeated upgrade never duplicate source awards or reset a
  farm that has already migrated.
- Edit/delete/undo affects all eligible fields; timestamp edits and restored source identities do
  not bypass cutoffs. Old and newly added backdated sources follow the documented rules.
- Source deletion below spent totals leaves harvests/badges permanent and preserves the deficit.
- Field 4/5 unlock at 5/15 active-generation harvests, receive only later sources and do not relock.
- Repeated harvest taps, concurrent timer settlement and unlock boundaries cannot overspend.
- Migration preserves unrelated row counts, IDs, values and running timers. Test zero sources,
  no farm, mature crops, old compost, five unlocked fields and a large historical ledger.
- A pre-commit failure restores the old state; retry produces exactly one rebuild and archive.
- Format 1 import after an earlier upgrade still rebuilds; format 2 round trip preserves cutoffs,
  deficits, identities and the archive. Invalid/future backups leave the database intact.

### Timer layout

- Use the target 1216 x 2640 / 480 dpi emulator/device. Test both themes, English/Chinese and font
  scales 1.0x/1.3x; relaunch after font changes. Add 320/360 dp narrow cases and >=600 dp cases.
- Short ordinary cards at 405 dp / 1.0x use two columns and aim for the 164-184 dp height budget.
  Increase target visible card count against the recorded baseline when the baseline is taller.
- Check short/long names, large durations, linked task titles, comments, goals, no goal, no records,
  and 0/1/multiple running timers. The last row must scroll clear of navigation and the FAB.
- Measure each card before and after start/stop with `uiautomator` bounds. Height must remain
  equal for the same configuration; use UI bounds, not image-derived tap coordinates.
- Large fonts and long text remain readable without clipping/ellipsis, with a row fallback when
  needed. Confirm touch targets, top-bar actions and card widths as well as screenshots.
- Save evidence under `build/screens/small` and restore emulator font/display overrides afterwards.

### Repository checks and release

Run `./gradlew test`, `./gradlew assembleDebug`, `./gradlew lintDebug` and
`./gradlew connectedDebugAndroidTest` with a running emulator. Preserve and validate the v1 schema,
commit the v2 schema and add a 1 -> 2 migration test. Update backup, farm and layout tests, English
and Chinese strings, `AGENTS.md` schema/behavior notes and both READMEs.

After qualification, bump app metadata and both settings version strings together. Release notes
must explain the farm reset and legacy archive, preserved source history, recalculated starting
balances, Room 1 -> 2 migration, format 1 import / format 2 export, and old-app restore limits.
Follow the repository's existing signing and publication procedure when release is authorized.
