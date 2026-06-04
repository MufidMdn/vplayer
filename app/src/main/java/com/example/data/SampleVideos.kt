package com.example.data

import com.example.data.database.VideoRecord

object SampleVideos {
    val list = listOf(
        VideoRecord(
            id = "online_bunny",
            title = "Big Buck Bunny (Animasi Klasik)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            duration = 596000L, // 9:56
            isOnline = true,
            folderName = "Streaming Online",
            resolution = "1080p FHD"
        ),
        VideoRecord(
            id = "online_sintel",
            title = "Sintel (Kisah Fantasi)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            duration = 52000L, // 0:52
            isOnline = true,
            folderName = "Streaming Online",
            resolution = "720p HD"
        ),
        VideoRecord(
            id = "online_tears_of_steel",
            title = "Tears of Steel (Sci-Fi Efek Visual)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            duration = 734000L, // 12:14
            isOnline = true,
            folderName = "Streaming Online",
            resolution = "1080p FHD"
        ),
        VideoRecord(
            id = "online_elephants_dream",
            title = "Elephant's Dream (Animasi Surrealis)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            duration = 653000L, // 10:53
            isOnline = true,
            folderName = "Streaming Online",
            resolution = "1080p FHD"
        ),
        VideoRecord(
            id = "online_subaru",
            title = "Subaru Test Drive Off-Road",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            duration = 10000L, // 0:10
            isOnline = true,
            folderName = "Streaming Online",
            resolution = "720p HD"
        )
    )
}
