# eGauge temporary product website

Public preview: https://egauge.majjix.com/

Dependency-free HTML, CSS, and JavaScript. All gauge illustrations and readings on the website are concepts and sample data, not physical-device evidence. The site presents the product as in development. It collects optional preorder interest and custom project requests in a private SQLite database. It accepts no orders, deposits, or payments. Pricing, availability, final specifications, and compatibility must be confirmed before adding commerce. Search indexing is discouraged with both robots directives and HTTP headers; the page remains publicly accessible.

## Local preview

```sh
python3 -m http.server 18766 --bind 127.0.0.1 --directory website
```

Open http://127.0.0.1:18766/.

## Hosting

- SSH: `lstepnio@one.majjix.com`, using existing SSH key authentication.
- Main Compose file: `/docker/docker-compose.yml`.
- Service/container: `egauge`.
- Public files: `/docker/appdata/egauge/site/`.
- Server configuration: `/docker/appdata/egauge/nginx.conf`.
- Network: existing Compose `caddy` network, physically `docker_caddy`.
- The service labels let the existing Caddy Docker proxy route the hostname and manage HTTPS. The service publishes no host port.
- Non-root Nginx image pinned to the deployment digest, read-only filesystem/content/configuration, temporary `/tmp`, all capabilities dropped, health check, bounded logs.

`deployment/compose-service.yml` is a service fragment under the existing `services:` mapping, not a standalone Compose file. It is already integrated on the host. Do not append it a second time.

The initial deployment preserved all existing normalized Compose settings and started only `egauge`. The pre-change Compose backup is `/docker/backups/docker-compose.pre-egauge-20260929T023331Z.yml`. A pointer is also recorded in `/docker/appdata/egauge/compose-backup-path.txt`.

## Updating

Publish only `index.html`, `styles.css`, `app.js`, `favicon.svg`, `robots.txt`, `police-alert-concept.png`, and `report-hazard-concept.png` to the public document root. Store `interest-api/server.py` outside the document root and keep the database in a private directory. Never expose the repository root, deployment files, or environment files as the document root.

Back up `/docker/appdata/egauge/site` before an update. Stage changed files outside that directory and move each file into place after successful upload, publishing HTML last. Static file changes require no container restart. Server configuration changes require `docker exec egauge nginx -t` followed by a scoped reload or recreation. For Compose changes, validate first and run only:

```sh
docker compose -f /docker/docker-compose.yml config --quiet
docker compose -f /docker/docker-compose.yml up -d --no-deps egauge
```

Do not restart the whole stack. The host has unrelated pre-existing uncommitted changes.

## Verification

- Confirm HTTPS serves every public file and deployed bytes match local files.
- Confirm HTTP redirects to HTTPS and the container is healthy.
- Check live reading/layout controls and FAQ disclosures.
- Review desktop, 390-pixel mobile, and 320-pixel narrow layouts, horizontal overflow, and browser errors.
- Confirm existing containers retain their IDs and the original Majjix sites still respond.

## Rollback

For content changes, restore the saved static files. To remove the initial deployment, stop and remove only the `egauge` service using Compose, then remove its service block from the current Compose file. Caddy removes the route when that container disappears. Preserve the appdata directory for recovery. Restore the full pre-deployment Compose backup only if no subsequent changes have been made by other work; otherwise remove just the eGauge block.

## Interest and custom-work intake

The `egauge-interest` service stores email, selected deposit/VIP interests, consent text, and custom project requests in `/docker/appdata/egauge/private/interest.sqlite3`. The database must be accessible only to the service and host administrator, backed up, and excluded from public static files. It does not send email or notify an operator yet. Review submissions through a secure host session and respond manually. The site collects roadmap suggestions and optional interest in later deposit and VIP programs; those programs have no checkout or paid benefits yet. Do not present signup as an order or reservation.

Before accepting refundable deposits, decide the deposit amount, unit allocation rules, refund deadline and method, fulfillment region, estimated delivery, and payment provider. Publish those terms and wire checkout plus payment webhooks and refund handling. VIP benefits and any custom-work contract need separately defined scope and terms.

The initial vehicle targets are stock Jeep JKs and Hemi-swapped Jeep JKs. Adjacent Mopar work follows, with Volvo C30 and Porsche 718 available for evaluation. GX470 and FJ Cruiser are lower priority candidates. This is a provisional priority list, not a compatibility claim.

A daily 02:30 host cron job runs `/docker/appdata/egauge/interest-api/backup.py`, using SQLite's online backup API. Backups stay in `/docker/backups/egauge-interest` with private permissions and a 30-day rolling retention. To inspect counts without exposing contact data in logs:

```sh
python3 -c 'import sqlite3; db=sqlite3.connect("/docker/appdata/egauge/private/interest.sqlite3"); print(db.execute("select count(*) from interest").fetchone()[0], db.execute("select count(*) from custom_request where status="new"").fetchone()[0])'
```

For custom work, review the request, hold a discovery conversation, validate feasibility, then issue a written scope and quote before taking payment.

The public roadmap also describes an exploratory opt-in integration for crowdsourced road-hazard and police-presence alerts. It is not a current capability or first-product commitment.

The road-awareness marketing images are `police-alert-concept.png` and `report-hazard-concept.png`, generated future interface concepts using sample data. Keep its visible concept disclosures whenever publishing or reusing it.
