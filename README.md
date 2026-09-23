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
                 ├─ On-demand resolver: /api/streamed/streams?source=&id=
                 └─ Admin API (token → 24h session, match CRUD, site content)
                       │
index.html  ◀─────────┘  Public site (match cards, stream picker)
admin.html + admin.js     Admin SPA (login, matches, content, sync)
```

## File Structure

```
Sportzfylive-V2/
├── worker.js        # Cloudflare Worker: API, auth, Streamed sync + resolver
├── index.html       # Public site (embedded CSS and JavaScript)
├── admin.html       # Admin shell (markup + styles)
├── admin.js         # Admin SPA logic (login, match CRUD, sync)
├── test/            # Functional smoke tests (node test/resolver.smoke.mjs)
├── wrangler.jsonc   # Worker config (KV, queue, cron, assets)
└── README.md        # This file
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

2. Open `index.html` in your browser or deploy to GitHub Pages

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

Three Node smoke suites cover the UI contracts and the Worker logic. Run them all:

```bash
bun run test
# equivalent to:
#   node test/dom-ux.smoke.mjs    # keyboard access, modal behavior, stream picker selection
#   node test/ui-polish.smoke.mjs # design-system structure, layout order, a11y semantics
#   node test/resolver.smoke.mjs  # Streamed sync, per-source failure isolation, API shaping
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

## Deployment

### GitHub Pages

The repository is configured for automatic deployment to GitHub Pages:

1. Push changes to the `main` branch
2. GitHub Actions automatically builds and deploys
3. Access at: `https://svimran46.github.io/Sportzfylive-V2/`

### Worker Deployment (Cloudflare)

The backend is a Cloudflare Worker configured in `wrangler.jsonc`:

```bash
npx wrangler deploy          # deploy worker.js with KV, queue and cron bindings
npx wrangler secret put ADMIN_TOKEN   # set the admin token secret
```

After deploying, run one admin sync (Admin panel → Sync & Automation) to populate the match list and per-source stream caches.

### Self-Hosted

Simply copy all files to your web server and ensure:
- `index.html` is the document root
- All files are in the same directory
- HTTPS is enabled (recommended for embedded content)

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
