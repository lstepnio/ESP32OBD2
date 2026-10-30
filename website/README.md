# Project website

Static source for [egauge.majjix.com](https://egauge.majjix.com/). Keep the existing
visual style and contact forms. Product claims follow the authorities listed in
[content.json](content.json); update its review date only after checking those sources.
Catalog size is derived from the reading contract, not a vehicle compatibility claim.
Material claims carry named audience, availability, implementation source and evidence.
These are authored judgments for review, not a claim that a file path proves truth.
Gallery choices/captions/alt text and action availability are build-time fields in
`content.json`, traced to source authorities. The builder escapes visible text and
checks referenced images; no extra runtime fetch or dependency is used.

## Maintenance cadence

The website is a visually appealing, benefit-led view of actual project progress,
not a second engineering backlog. Maintain it as features, functionality and UI
change, including accepted owner feedback, priority changes and usability improvements.

- At feature completion, review website copy, roadmap and screen impact. Record any
  pending website delta in the PR summary or the existing affected backlog task,
  rather than creating another task list. Prepare changes alongside the feature.
- Batch public website refreshes into **at most one publication per calendar day in
  America/Denver**. Check the live root `index.html` modification time and manifest
  before publishing; `content.json.reviewedOn` is a content-review date, not proof
  of publication time. If already published that day, hold further changes for the
  next eligible day. Rollback of a failed publication remains available.
- The daily batch is best-effort while an agent is actively working, not a promise
  of unattended publication. Proceed only with ample token/context and execution
  headroom after essential product work. Otherwise leave a concise pending delta
  in the PR/backlog and defer; never skip evidence or visual checks to force a date.
- At the next eligible refresh, review accepted changes since the live manifest's
  source commit and reconcile `docs/current-state.md`, `docs/roadmap.md` and
  `docs/backlog.md`. Reflect the owner's direction and discovered improvements;
  separate available, in-development and exploratory work. Do not invent delivery
  dates, compatibility or hardware qualification. Update the content review date
  only after this review.
  Recheck vendor pricing/specifications when quoting them; identify board-only
  prices and separate adapters, shipping and taxes from the advertised board cost.
- Review App and gauge screenshots in that same batch. Refresh affected screens
  whenever visible UI changes make an image obsolete; include representative new
  features and retire superseded images. Reuse existing Compose/production LVGL
  captures from feature checks, generating only missing views. Unchanged screens
  need no recapture. Follow [screen provenance](#screens-and-provenance).
- Show round gauge displays as **circles**, using a circular device shell, clipping
  or transparency. Never present a round display as a bare square screenshot,
  stretch its aspect ratio, or crop away its intended content. Rectangular layout
  cards and photographs are fine when the gauge itself remains visibly round.
- Keep copy concise, layouts cohesive and screenshots legible on desktop/mobile.
  Review the page visually, including the public roadmap, before publication.
  Use screen names in captions; do not add “Demo” or “Simulated” badges. Retain
  evidence/provenance separately and clearly identify unavailable future concepts.
- Screenshot collection, marketing packaging and deployment are a separate daily
  batch, never a prerequisite for normal App/firmware builds or every commit.
  Keep the existing fast website validation; do not add full capture or publication
  jobs to the default build/CI path. Record successful publication time, source
  commit and private backup location in the deployment receipt/task outcome.

## Brand and editorial direction

Present eGauge as Luke's hands-on demonstration of thoughtful software on capable,
affordable hardware. The personal brand is expressed through clear design decisions,
real progress and useful experiences. Confident, curious and occasionally playful
copy fits; generic superlatives and promises of universal compatibility do not.

A useful narrative is: **Small hardware. Thoughtful software. Built around you.**
Follow it with a concrete outcome, a current screen or short interaction clip, and
a clear invitation to try the development platform or discuss a custom project.
Use the existing Lukasz (Luke) Stepniowski credit and profile links. Do not invent
credentials, customers, testimonials, performance results or a larger team.

- Lead with what someone can do and why it is interesting. Explain engineering only
  when it helps them choose hardware, understand availability or take the next step.
- Show the product first, then a short build story: problem, design choice, observed
  result and what is next. Link GitHub evidence for readers who want depth.
- Distinguish **Available on the development platform**, **Being tested**, and
  **Exploring** in relevant sections. An owner-only installed action is not public
  support. Pair each material claim with its source/evidence reference in content
  metadata; a file existing is not proof that the claim is true.
- Keep labels understandable and scoped. Avoid a wall of caveats, but retain material
  limits next to the relevant claim rather than hiding them in a footer.
- Share the same App/gauge assets with GitHub. Use real production-rendered states,
  circular gauge presentation, consistent colors and legible crops. Keep technical
  provenance off the hero; future concepts must still be identified as future work.
- Refresh the public roadmap around outcomes, using owner priorities and evidence.
  Avoid release-number diaries, speculative dates and a list of every internal task.

Before publication, answer: is this useful, distinctive, visually convincing and
supported by the current implementation/evidence for the audience named? If not,
rewrite or defer that item. The [product delivery loop](../docs/documentation.md#product-quality-and-public-story)
connects this editorial review to feature work. It does not authorize automatic
LinkedIn posts, testimonials or changes to customer consent.

## Build and preview

Use the repository Python environment from the [root setup](../README.md#development):

```sh
python tools/build_website.py --check --review-report artifacts/website-review.json
python tools/build_website.py --downloads artifacts/website-downloads --output artifacts/website-preview
python3 -m http.server 8771 --bind 127.0.0.1 --directory artifacts/website-preview
```

Use an empty output directory for each build. Prepare verified downloads using the
[USB recovery package procedure](../web-recovery/README.md#package-provenance-and-updates).
The default offline check validates metadata; a deployable build requires every binary. Host lint runs the website validator
and JavaScript syntax checks. The build checks local links, anchors, image alt text,
and source-document paths. Open the preview on desktop and mobile, exercise all
screen buttons, and check images and overflow. Never submit contact forms with test
data to the real API; use browser request interception for success/error checks.

## Screens and provenance

App images come from the existing Compose capture procedure in
[quality guidance](../docs/development/quality.md). Gauge images use the production
LVGL host renderer and [gallery exporter](../tools/render_gauge_gallery.py).
Both live in `docs/assets/`, shared with the GitHub README. Captions use screen names.
The Gauge/Car/Settings marketing set uses 390 × 844 dp production composables at density 2
(780 × 1688 pixels), ordinary three-tab navigation and synthetic example data;
`docs/assets/app/marketing-provenance.json` records its source and hashes. Actions uses a native modal screenshot at1080×1920; its separate capture method is recorded there. This is
not physical-device or vehicle evidence. Native gauge captures can use the existing
host CMake option `EGAUGE_HOST_RGB565=ON`; default test rendering remains unchanged.
The gallery exporter includes Dual + arc and the current combined CEL/threshold
frame. Preserve native files; do not upscale a 240-pixel panel to invent detail. The gallery exporter keeps visible RGB pixels and makes nonexistent circular-panel corners transparent, so the same files work on GitHub and the site.
The site publishes WebP alternatives for the large future concepts with PNG fallback.
The dated `review/` report/captures record this audit and its verification limits;
they are not a second feature-status authority.
The builder requires hash-matched provenance for every placed App/gauge image,
including dynamically selected gallery screens. Relevant renderer, layout, theme,
resource and capture-harness inputs are hashed at the recorded capture revision.
A changed, added or removed input creates an item in `site-review.json` and the
manifest. Offline `--check` reports these items without recapturing; deployable
builds reject unresolved items and pending representative captures. Asset age by
itself never requires a new capture. This remains an inexpensive check, outside
normal firmware and App capture/build work.

For each changed-input item, inspect the actual diff and represented states. If the
image changed, run the existing production capture/exporter and update its entry
hash and capture input baseline, recording source revision, renderer/options and
synthetic-data limits. Preserve the shared `docs/assets/` images used by GitHub.
Never replace old input hashes with current hashes merely to pass the check.
If the visible represented states remain equivalent, retain the capture baseline
and add `equivalenceReview` to that provenance JSON: `reviewedOn`, a specific
`reason`, repository `evidence` paths, and `sha256` containing the exact current
relevant input map. That acknowledgement accepts only those bytes; subsequent
drift reopens review. A date or source commit alone cannot acknowledge it.

Material copy used through `CLAIM_*` tokens has a mandatory `claims` record;
`claimScopes` names the audience and availability, and each claim/action status
links implementation sources plus evidence. The manifest includes these records
and the full image metadata. Review semantics and audience before changing copy:
ordinary public controls remain disabled, owner-only candidates are separate, and
Burnout is paused.
`claimInputProvenance` holds one shared SHA-256 map for claim sources, evidence and
source authorities. Missing baseline/review metadata fails the check. Changed,
newly referenced or removed sources create `changedClaimInputs`, listing affected
claims and the exact current hash map, and block deployable builds. Inspect the
actual changes against the named audiences and availability, revise claims when
needed, then update `reviewedOn`, a specific semantic-review `reason` and `sha256`
with the report's current map. Do not rehash sources merely to make publication
pass. The metadata check remains fast and reports unresolved drift without
claiming it was reviewed. Keep repeated action availability in `actionAvailability`, never
as independently maintained HTML assertions. `pendingCaptures` is the explicit
publication gate for missing representative views, not permission to use stale
screens. Clear it only after saving/reviewing the production capture or deciding
with the owner that the view is outside the public batch.

Future road-awareness illustrations are stored separately in `future/` and must
remain explicitly described as unavailable future concepts.

## Deployment and rollback

The existing Nginx service reads `/docker/appdata/egauge/site` on `one.majjix.com`.
An authorized static update needs no service restart or Docker access.

1. Commit and push reviewed source, then build a fresh output directory. Its
   `site-manifest.json` records that commit, source authorities and file hashes.
2. Inspect live content for concurrent changes. Back up the complete current site
   outside the webroot under `/docker/appdata/egauge/private/backups/`.
3. Upload to a unique staging directory, verify every manifest hash, then copy the
   content-addressed `assets/` directory into the existing webroot. Keep old assets
   so clients with older HTML continue to work.
4. Publish new `/downloads/` paths before HTML and retain earlier immutable downloads.
   Replace root metadata, `/recovery/index.html`, and finally root `index.html` using temporary files and
   atomic file renames **inside the existing site directory**. Do not rename the
   directory itself: the container bind mount retains its inode.
5. Verify public HTTPS HTML/assets against the build, desktop/mobile rendering,
   screen buttons, console errors and unchanged security headers/API routes.
   Remove only the staging directory created for this publication.

The [Nginx site configuration](deployment/nginx.conf) includes the fixed, cached public GitHub release endpoint described in [release selection](../web-recovery/README.md#release-selection-and-local-packages), and relative directory
redirects for Caddy HTTPS proxying. Applying a configuration change requires
`docker exec egauge nginx -t` then `docker exec egauge nginx -s reload` by an
authorized Docker administrator; static publications need neither command.

Preserve Caddy configuration, Compose, contact service/data, consent boundaries for existing signup records,
no-index policy and unrelated files. Contact endpoints remain same-origin
`/api/interest` and `/api/custom`. Never commit server backups or raw diagnostics.

If verification fails, restore the backed-up root files atomically after checking
that nobody has published newer content. Old assets can remain. Record the deployed
commit and private backup location in the task outcome; do not duplicate project
status here.

## Public offer

The owner’s distribution direction is [free DIY software and custom projects](../docs/roadmap.md#distribution-and-custom-work).
Free public firmware applies to the original reference board only. Additional
hardware integrations/releases default to private under the [platform policy](../docs/architecture/platforms-and-releases.md). Do not imply free firmware for every future board.
Use project-update signup language, not preorders, deposits, reservations or paid
roadmap access. A finished unit can be discussed on request; do not imply a retail
launch commitment. Consent text and success messages must match the visible form.
New copy does not authorize repurposing existing signup records or changing their
recorded preferences. Preserve the existing API and data during copy-only changes.

## Intake source and operational checks

`interest-api/server.py` and `interest-api/backup.py` own the maintained contact
service and SQLite backup. Host regression tests use temporary synthetic data only.
The static site publishes neither source nor database. Source preparation does not
mean the host service has been deployed or restarted.

The service uses `INTEREST_DB` (default `/data/interest.sqlite3`) and `SITE_ORIGIN`.
Its `/health` and `/ready` check a write reservation and required tables without
changing records. Initialization adds the append-only `consent_event` table; it
retains original `interest.consent_text` values. New forms send `consent_version`
`project-updates-v1`, whose server-owned text matches the form. Requests from cached
old forms without a version retain `legacy-preorder-v1`; unsupported versions fail.
Repeat signup appends an acceptance event rather than rewriting historical consent.
Removal workflows must include both the original record and consent events.

Configure `TRUSTED_PROXY_CIDRS` with the **exact** Nginx and Caddy proxy addresses
or narrowly scoped networks verified on the deployment. The server walks forwarded
addresses from its trusted socket peer inward; client-supplied left values cannot
choose an identity. Empty configuration trusts no forwarding header and limits by
socket peer, so a proxied deployment must configure its actual trusted hops before
rollout to avoid sharing one throttle across visitors. Never trust all addresses.
Validate the real proxy chain separately; a local fixture cannot establish edge behavior.

Operational logs contain bounded outcome/category/duration fields, never submitted
email, text, payloads or raw SQLite exception text. Expected malformed bodies return
400; database errors return 503 and fail readiness. Restore and alerting ownership
remain with the host administrator.

The backup command accepts `INTEREST_DB` and `INTEREST_BACKUPS` environment paths;
host defaults remain `/docker/appdata/egauge/private/interest.sqlite3` and
`/docker/backups/egauge-interest`. It opens the existing source read-only, validates
schema/integrity, creates a private temporary snapshot, verifies it, then publishes
atomically. Thirty-day retention runs only after a verified snapshot. Missing or
invalid source fails nonzero without publishing or pruning. Schedule failure alerts
and rehearse restoration against an isolated database before relying on backups.

For a scoped backend rollout: identify the running service/source hashes and exact
proxy hops; take a verified database backup plus source/config backup; stage only
these scripts outside the webroot; use an isolated restore to validate the additive
migration; replace scripts atomically and restart only `egauge-interest`. Check its
local readiness and sanitized logs, then exercise the proxy chain with authorized
synthetic requests. Roll back scripts from backup if needed; the additive event table
can remain without rewriting older consent. Do not run a whole-stack restart or put
customer records into test logs. This is a procedure, not a record of live execution.
