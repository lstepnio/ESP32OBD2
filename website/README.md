# Project website

Static source for [egauge.majjix.com](https://egauge.majjix.com/). Keep the existing
visual style and contact forms. Product claims follow the authorities listed in
[content.json](content.json); update its review date only after checking those sources.
Catalog size is derived from the reading contract, not a vehicle compatibility claim.

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
- At the next eligible refresh, review accepted changes since the live manifest's
  source commit and reconcile `docs/current-state.md`, `docs/roadmap.md` and
  `docs/backlog.md`. Reflect the owner's direction and discovered improvements;
  separate available, in-development and exploratory work. Do not invent delivery
  dates, compatibility or hardware qualification. Update the content review date
  only after this review.
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

## Build and preview

Use the repository Python environment from the [root setup](../README.md#development):

```sh
python tools/build_website.py --check
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

Preserve Caddy configuration, Compose, contact service/data, consent wording,
no-index policy and unrelated files. Contact endpoints remain same-origin
`/api/interest` and `/api/custom`. Never commit server backups or raw diagnostics.

If verification fails, restore the backed-up root files atomically after checking
that nobody has published newer content. Old assets can remain. Record the deployed
commit and private backup location in the task outcome; do not duplicate project
status here.
