// channels.js
async function loadChannels() {
    try {
        // Fetch both channels and genres in parallel for speed
        const [channelsRes, genresRes] = await Promise.all([
            fetch('https://timst.cfd/api/channels'),
            fetch('https://timst.cfd/api/genres')
        ]);

        if (!channelsRes.ok || !genresRes.ok) {
            throw new Error('Network response was not ok');
        }

        const channelsData = await channelsRes.json();
        const genresData = await genresRes.json();

        // Build a genre ID → Name map (e.g., 2 -> "Sports")
        const genreMap = {};
        if (genresData && genresData.genres) {
            genresData.genres.forEach(g => {
                genreMap[g.id] = g.name;
            });
        }

        // Map API channels to the format our player expects
        const formattedChannels = channelsData.channels.map(ch => {
            // Extract the first stream URL (embed link)
            const streamUrl = ch.streams && ch.streams.length > 0 ? ch.streams[0].url : '';
            
            return {
                name: ch.name,
                logo: ch.logo || '',
                genre: genreMap[ch.genre] || 'General',
                url: streamUrl
            };
        }).filter(ch => ch.url !== ''); // Remove channels with no stream URL

        return formattedChannels;

    } catch (error) {
        console.error('Failed to load channels from API:', error);
        throw error; // Re-throw so index.html can catch it and show an error message
    }
}

// Make the function globally available
window.loadChannels = loadChannels;
