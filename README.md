# Sportzfylive V2

A modern, responsive web application for streaming sports channels with HLS and iframe embed support.

## Features

- 🎬 **Smart Player**: Automatically detects and plays HLS streams (.m3u8) or iframe embeds
- 🔍 **Advanced Search**: Real-time search across all available channels
- 🏷️ **Category Filtering**: Filter channels by sport type (Soccer, NCAA Sports, Multi-Sport)
- 📱 **Responsive Design**: Fully responsive grid layout for mobile, tablet, and desktop
- 🛡️ **Secure**: XSS protection with safe DOM manipulation
- ⚠️ **Error Handling**: User-friendly error messages for playback failures
- 🎨 **Modern UI**: Dark theme with smooth animations and transitions

## File Structure

```
Sportzfylive-V2/
├── index.html       # Main HTML file with embedded CSS and JavaScript
├── channels.js      # Channel data configuration
├── README.md        # This file
└── .github/         # GitHub configuration (auto-generated)
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

### Adding Channels

Edit `channels.js` and add new channel objects:

```javascript
{
  "name": "Channel Name",
  "logo": "https://example.com/logo.png",
  "genre": "Soccer",  // or "NCAA Sports", "Multi-Sport"
  "url": "https://stream-url.com/stream.m3u8"  // or iframe embed URL
}
```

### Supported Genres
- Soccer
- NCAA Sports
- Multi-Sport

### Playback

- **HLS Streams (.m3u8)**: Automatically played via hls.js
- **Iframe Embeds**: Displayed in a sandboxed iframe
- **Search**: Type to filter channels by name
- **Filter**: Click category buttons to filter by genre

## Technologies Used

- **Frontend**: HTML5, CSS3, Vanilla JavaScript
- **Player**: hls.js for HLS stream playback
- **Video API**: Native HTML5 `<video>` element

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

### Channels not loading?
- Verify `channels.js` is in the same directory as `index.html`
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
