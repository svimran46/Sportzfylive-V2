const channels = [
    {
        name: "BBC Two",
        category: "entertainment",
        logo: "https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/BBC_Two_logo_2021.svg/1200px-BBC_Two_logo_2021.svg.png",
        url: "http://193.239.187.97/BBC2/index.m3u8"
    },
    {
        name: "Sky Sports F1 UHD",
        category: "sports",
        logo: "https://i.ibb.co/1tmrNyj9/Sky-Sport-F1-UHD.png",
        url: "http://czstream.com:826/live/LouCarey/KYsHfE1YLU/118946.m3u8"
    },
    {
        name: "Hub Premier 4 FHD",
        category: "sports",
        logo: "https://r2.thesportsdb.com/images/media/channel/logo/emwu7x1659622075.png",
        url: "http://tbtv.me:2095/live/383301917192/909365136076/1309858.m3u8"
    },
    {
        name: "FanDuel Sports Florida",
        category: "sports",
        logo: "https://i.imgur.com/S5cqDnq.png",
        url: "http://raccoon.bz:8080/live/leo24/leo24/44980.m3u8"
    },
    {
        name: "FanDuel Sports Midwest",
        category: "sports",
        logo: "https://i.ibb.co/5W31SKh5/Fan-Duel-Midwest.png",
        url: "http://raccoon.bz:8080/live/leo24/leo24/44987.m3u8"
    },
    {
        name: "BeIN Sports USA",
        category: "sports",
        logo: "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/BeIN_Sports_logo.svg/1200px-BeIN_Sports_logo.svg.png",
        // IMPORTANT: For BeIN, you must extract the raw .m3u8 link using F12 -> Network.
        // The iframe link will NOT work in this player.
        url: "YOUR_EXTRACTED_BEIN_M3U8_LINK_HERE"
    }
    // Add more channels here...
];
