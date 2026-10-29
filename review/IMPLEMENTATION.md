# Approved website implementation and delivery

The owner approved all nine report items, commits/pushes and the steps needed for
build/publication, with prompts only for critical blockers. This follows the dated
[review](REPORT.md); the initial matrix/captures remain pre-fix evidence.

## Implemented source and artifacts

- Visible mobile Try it free path and separate Android/desktop recovery links.
- Precise fixed-idle/candidate/draft/disabled status near action claims; Auto and
  future integrations retain qualification limits. No runtime flags changed.
- Current three-tab App captures at 780×1688 with synthetic data, native RGB565
  gauge screens including Dual + arc and combined fault/threshold presentation.
- Gallery/caption/status authority in existing content.json; escaped static output,
  referenced source/image checks and responsive source/cross-page anchor validation.
- Incorrect full-backup advice removed; original no-uninstall warning retained.
- Current same-signer dev.53 APK and a reusable APK-only packaging path. Reviewed
  dev.60 fallback and immutable old download URLs are retained.
- Large future-concept WebP alternatives: 158,532 bytes combined versus 3,624,511
  bytes of PNG fallback, about 95.6% smaller for supporting browsers. Originals stay.
- Shorter hardware copy, field-adjacent email label, social image/metadata and removal
  of proven-unused legacy gauge CSS. APIs, consent and no-index/security policy retained.

## Android build and preservation

A fresh clean debug build passed. APK SHA256:
`f47c10aa9cc9b6afca2e84ef9d5574e46fdf7bf10fcf1b93b43e48abeac10280`.
Package com.lstepnio.egauge, dev.53/code 53, min SDK29, target36. Signature verifies
with the same pinned debug certificate; source revision is operator attestation,
not a signed embedded source identity. Android production code is unchanged.

On a dedicated disposable API35 emulator, the actual pinned dev.51 APK decoded a
seeded legacy schema1 profile with RPM Arc/coolant thresholds, appearance preferences
and one diagnostic history event. Replacement with the fresh code53 APK retained
raw profile/preferences/event, while the decoded profile migrated14→19. This bounded
fixture does not establish every possible prior-profile upgrade or live radio path.
[Before](android-upgrade-before.json) / [after](android-upgrade-after.json) retain the
synthetic proof. The owner phone/gauge were not used.

## Verification and delivery

Final local checks passed:177 Python tests (including11 focused package/site tests),
14 review-server tests, native host fixtures,42 recovery Node tests, host lint and formatting. The
focused2x marketing capture passed1/1 in41s; firmware default/RGB565 fixture results
remain separately recorded. Fresh page/layout checks at375/768/1440 had no overflow,
missing images, console errors or HTTP failures; seven gallery options worked. Eight
mocked form cases passed and90 settled keyboard focus steps remained visible.
The after text-contrast sample passed; a low-contrast new action caption was caught,
fixed and its failed intermediate result retained.

| Page/preset | Before P/A/BP/SEO | After P/A/BP/SEO |
| --- | --- | --- |
| Home mobile |100/100/96/66|100/100/96/66|
| Home desktop |100/100/100/66|100/100/100/66|
| Recovery mobile |92/100/100/63|93/100/100/63|
| Recovery desktop |99/100/100/63|99/100/100/63|

Single local Lighthouse13.0.1 observations are not production distributions or a full
WCAG certification. Low SEO remains deliberate no-index; native-panel image scaling
remains the mobile best-practice tradeoff. No accessibility gate was weakened.
At375 px, the first install route moved from about6,224 px down the homepage to the
header at23 px. Recovery now has direct phone/computer branch links near the title.
All65 matrix rows are aligned or explicitly deferred to canonical help/qualification.
The deployed source is independently recorded in the public
[site manifest](https://egauge.majjix.com/site-manifest.json). Compare its sourceCommit
to the checked PR head; a source commit or CI result alone does not prove public
deployment. Private deployment receipts retain backup path, exact source and hashes.
No physical-device installation is implied by this website update. Firmware dev.75 remains current;
website/test-harness work needs no new firmware release.
