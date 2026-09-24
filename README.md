# Sportzfylive V2

A modern, responsive web application for streaming live sports matches. The public site is static HTML/JS; the backend is a Cloudflare Worker that syncs matches from the Streamed API, resolves per-source stream metadata, and manages admin content.

## Features

- ⚽ **Match Listing**: Live and upcoming fixtures grouped by sport, synced from the Streamed API every 10 minutes
- 🎬 **Smart Player**: Automatically detects and plays HLS streams (.m3u8) or iframe embeds
- 🔍 **Advanced Search**: Real-time search across all available matches
- 🏷️ **Category Filtering**: Filter matches by sport (FOOTBALL, CRICKET, TENNIS, and more)
- 🛡️ **Secure**: Playback URLs are resolved server-side only and never exposed to the client; XSS-safe rendering
- ⚠️ **Error Handling**: Per-source failure isolation — one bad Streamed source never breaks a match list
- 📱 **Responsive Design**: Fully responsive grid layout for mobile, tablet, and desktop
- 🎨 **Modern UI**: Dark theme with smooth animations and transitions

## Architecture

```
Streamed API ──▶ Cloudflare Worker (worker.js)
                 ├─ 10-min cron sync + source resolution (SWR cache in KV)
                 ├─ Public API: /api/matches/all-today (sanitized, no embedUrl)
                 ├─ Image proxy: /api/images?url= (same-origin posters/crests, streamed.pk only)
                 ├─ On-demand resolver: /api/streamed/streams?source=&id=
                 └─ Admin API (token → 24h session, match CRUD, site content)
                       │
index.html  ◀─────────┘  Public site (match cards, stream picker)
admin.html + admin.js     Admin SPA (login, matches, content, sync)
```

## File Structure

```
Sportzfylive-V2/
├── worker.js          # Cloudflare Worker: API, auth, Streamed sync + resolver
├── index.html         # Public site (embedded CSS and JavaScript) — source copy
├── admin.html         # Admin shell (markup + styles) — source copy
├── admin.js           # Admin SPA logic (login, match CRUD, sync) — source copy
├── og-card.png        # Social share card — source copy
├── public/            # The entire published site (Worker static assets)
│   ├── index.html     #   published copy of the root pages above
│   ├── admin.html
│   ├── admin.js
│   ├── og-card.png
│   ├── fonts/         #   self-hosted DM Sans woff2
│   ├── _headers       #   response headers for static assets
│   ├── robots.txt
│   └── sitemap.xml
├── test/              # Node smoke suites (UI contracts, worker logic, deploy)
├── .github/workflows/ # Cloudflare deploy on push to main
├── wrangler.jsonc     # Worker config (KV, queue, cron, assets)
└── README.md          # This file
```

## Getting Started

### Prerequisites
- A modern web browser (Chrome, Firefox, Safari, Edge)
- For HLS streams: Browser must support Media Source Extensions (MSE)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/svimran46/Sportzfylive-V2.git
cd Sportzfylive-V2
```

2. Preview locally (serves the same `public/` directory the Worker publishes):
```bash
bun install     # installs wrangler for deploys
bun run serve   # http://localhost:3000
```

The local server has no API — match data and the `/api/images` poster proxy come
from the deployed Worker, so the page talks to `PROXY_URL` in `index.html` while
you develop.

## Usage

### Managing Matches

Matches are synced automatically from the Streamed API every 10 minutes. Admins can add manual matches, edit metadata, and manage manual stream rows in the admin panel (`admin.html`), which authenticates against the Worker with an admin token.

### Playback

- **HLS Streams (.m3u8)**: Automatically played via hls.js
- **Iframe Embeds**: Displayed in a sandboxed iframe
- **Search**: Type to filter channels by name
- **Filter**: Click category buttons to filter by genre

## Technologies Used

- **Frontend**: HTML5, CSS3, Vanilla JavaScript
- **Player**: hls.js for HLS stream playback
- **Video API**: Native HTML5 `<video>` element

## Testing

Four Node smoke suites cover the UI contracts, the Worker logic and the deploy
configuration. Run them all:

```bash
bun run test     # or: npm test
# equivalent to:
#   node test/dom-ux.smoke.mjs    # keyboard access, modal behavior, stream picker selection
#   node test/ui-polish.smoke.mjs # design-system structure, layout order, a11y semantics
#   node test/resolver.smoke.mjs  # Streamed sync, per-source failure isolation, API shaping, image proxy
#   node test/deploy.smoke.mjs    # published copies in sync, no host leftovers, deploy workflow
```

The DOM and UI suites parse the real `index.html` and run its JavaScript against a DOM stub, so changes to markup, styles or interaction code are validated against the shipped page — not a copy.

## Error Handling

The application provides user-friendly error messages for:
- Network connectivity issues
- Invalid/corrupted streams
- Unsupported media formats
- Failed embed loading

Errors auto-dismiss after 5 seconds or when a new channel is selected.

## Security

- **XSS Prevention**: All user data is sanitized and rendered safely using DOM methods
- **Sandboxed Embeds**: Iframe embeds are sandboxed with limited permissions
- **Input Validation**: All data is validated before rendering

## Browser Support

| Browser | HLS Support | Iframe Support |
|---------|-------------|----------------|
| Chrome 43+ | ✅ Yes | ✅ Yes |
| Firefox 42+ | ✅ Yes (with MSE) | ✅ Yes |
| Safari 11+ | ✅ Yes (native) | ✅ Yes |
| Edge 79+ | ✅ Yes | ✅ Yes |

## Deployment (Cloudflare Workers)

One Worker is the whole production surface: it serves the API **and** the static
site in `public/` from the same origin, so there is no second host to keep in
sync. Bindings live in `wrangler.jsonc`.

### Deploy from your machine

```bash
bun install                            # installs wrangler
bun run deploy                         # wrangler deploy: worker.js + ./public
npx wrangler secret put ADMIN_TOKEN    # admin panel token (once)
```

Wrangler authenticates with `wrangler login`, or with the `CLOUDFLARE_API_TOKEN`
and `CLOUDFLARE_ACCOUNT_ID` environment variables when you want it
non-interactive. The site is live at
`https://sportzfylive.svimranmy.workers.dev/`.

### Secrets

Secrets never live in the repository. They go into the Worker:

```bash
npx wrangler secret put ADMIN_TOKEN   # admin panel token
```

For local work (`wrangler dev`), put the same names in `.dev.vars` at the repo
root — it is gitignored, as are `.env` and `.env.local`. `wrangler deploy` only
ever reads Worker secrets, so a local file can never be published.

### Deploy on every push (GitHub Actions)

`.github/workflows/deploy.yml` runs the smoke suites and then `wrangler deploy`
on each push to `main`. Add two repository secrets (Settings → Secrets and
variables → Actions):

| Secret | Value |
| --- | --- |
| `CLOUDFLARE_API_TOKEN` | API token created from the **Edit Cloudflare Workers** template |
| `CLOUDFLARE_ACCOUNT_ID` | Account ID from the Cloudflare dashboard |

### First-time setup on a fresh Cloudflare account

1. Create the KV namespace and put the printed id in `wrangler.jsonc`:
   ```bash
   npx wrangler kv namespace create SPORTZFY_DB
   ```
2. Create the sync queue — or, on the Workers **free** plan (Queues need the paid
   plan), delete the `queues` block from `wrangler.jsonc`; the 10-minute cron then
   runs the same sync inline in `scheduled()`.
3. `npx wrangler secret put ADMIN_TOKEN`
4. `bun run deploy`, then trigger one sync (Admin panel → Sync & Automation).

### Custom domain

Add a route in `wrangler.jsonc` and update the canonical origin in `index.html`
(`og:url`, the `canonical` link, `og:image`, `twitter:image`) plus
`public/robots.txt` and `public/sitemap.xml`:

```jsonc
"routes": [{ "pattern": "example.com", "custom_domain": true }]
```

### Caching and headers

`public/_headers` sets the static-asset response headers (`/fonts/*` cached for a
year, `nosniff` + `Referrer-Policy`); Worker-generated responses set their own.
Assets ship with `Cache-Control: public, max-age=0, must-revalidate` and an ETag,
so a deploy is visible immediately, while `/api/images` responses are cached at
the edge for 24 hours.

### Keeping the published copy in sync

The repo root holds the source of `index.html`, `admin.html`, `admin.js` and
`og-card.png`; `public/` holds the published copies. Edit the root file, copy it
into `public/`, and run `bun run test` — the deploy suite fails when the two
diverge. (`public/` is also the only thing uploaded, so anything placed there
becomes public.)

### Self-hosted static copy (optional)

`public/` can be dropped on any static host, but the API, the admin panel and the
`/api/images` poster proxy still run on the Worker — keep `PROXY_URL` in
`index.html` and `API` in `admin.js` pointed at the deployed Worker origin and
serve over HTTPS for embedded content.

## Performance

- **Lightweight**: ~18KB total (HTML + CSS + JS)
- **No Dependencies**: Except hls.js (CDN loaded)
- **Fast Load**: Optimized grid rendering for 100+ channels
- **Responsive**: Smooth animations at 60fps

## Troubleshooting

### Matches not loading?
- Verify the Worker is deployed and `/api/status` responds
- Check browser console for errors (F12 → Console)

### Streams not playing?
- Verify the stream URL is correct and accessible
- Check if the stream URL is CORS-enabled
- Some streams may require a VPN or geolocation

### Posters or team crests missing on desktop?
- Poster art is hotlinked from `streamed.pk`; a blocked or filtered request retries
  once through `/api/images?url=` (same origin) and then falls back to team initials
- If every image is missing, redeploy so `/api/images` is live: `bun run deploy`
- Matches the Streamed API ships without a badge (`badge: ""`) show team initials by design

### A change is not showing up on the live site?
- Run `bun run test`: the deploy suite fails when `public/index.html` drifts from `index.html`
- Confirm the latest deploy succeeded: `gh run list --workflow=deploy.yml`

### Performance issues?
- Clear browser cache (Ctrl+Shift+Delete)
- Close unused tabs
- Try a different browser

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is open source and available under the MIT License.

## Acknowledgments

- [hls.js](https://github.com/video-dev/hls.js) - HLS streaming library
- [Wikimedia Commons](https://commons.wikimedia.org/) - Channel logos
- All streaming partners and providers

## Support

For issues, questions, or suggestions, please open an issue on GitHub.
