# ViTune Desktop (Windows)

A desktop music player built on ViTune's code. It reuses ViTune's YouTube Music
provider for search and "radio" queues, yt-dlp for audio stream URLs (the same tool
the Android app uses internally), and VLC for playback.

## Features in this first version

- Search YouTube Music for songs
- Click a song to play it; the queue fills with similar songs (like ViTune's radio)
- Right-click a song for "Play next" or "Add to queue"; right-click in the queue to remove
- Play/pause, next, previous, seek bar and volume
- Keyboard shortcuts: Ctrl+P play/pause, Ctrl+→ next, Ctrl+← previous
- yt-dlp is downloaded automatically and updated on every launch

## One-time setup

Open PowerShell and install these two things:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK   # Java 21 (any JDK 21+ works)
winget install DenoLand.Deno                     # JavaScript runtime yt-dlp needs for YouTube
```

Close and reopen PowerShell afterwards so the new programs are on your PATH.
(Node.js also works instead of Deno if you already have it.)

### VLC (bundled into the installer)

Playback uses VLC, and the installer ships its own trimmed, audio-only copy (~40 MB),
so users don't need to install VLC. To build that in, download the **64-bit VLC 3.x zip**
from <https://get.videolan.org/vlc/> (for example `vlc-3.0.24-win64.zip`) and extract it
into the repository's top folder as `vlc-runtime`, so `ViTune\vlc-runtime\libvlc.dll` exists
(git ignores that folder). Or put it anywhere and pass its path:

```powershell
.\gradlew.bat packageMsi -PvlcDir=C:\path\to\vlc-3.0.24
```

VLC is never committed to git. If no VLC folder is found the build still works, but the
app then needs an installed 64-bit VLC (`winget install VideoLAN.VLC`). The app always
prefers the bundled copy and falls back to an installed VLC.

## Get the code

This folder must sit **inside** a copy of the ViTune repository, because it compiles
ViTune's provider code directly from `..\providers`:

```powershell
git clone https://github.com/bartoostveen/ViTune.git
# then copy this "desktop" folder into it, so you have ViTune\desktop\build.gradle.kts
cd ViTune\desktop
```

## Run it

```powershell
.\gradlew.bat run
```

The first build downloads Gradle and all libraries, so it takes several minutes.

## Make an installer

```powershell
.\gradlew.bat packageMsi     # or packageExe
```

The installer appears in `build\compose\binaries\main\msi\` (or `exe\`). It includes VLC
(see above), and yt-dlp is fetched by the app itself. Users only need Deno or Node.js.

VLC is licensed under the LGPL/GPL; its `COPYING.txt` is installed alongside it in
`app\resources\vlc\`.

## Where things are

| File | What it does |
| --- | --- |
| `model/MusicRepository.kt` | Search and radio, via ViTune's Innertube provider |
| `player/YtDlp.kt` | Downloads/updates yt-dlp and resolves stream URLs |
| `player/AudioEngine.kt` | VLC playback (replaces ExoPlayer from the Android app); finds the bundled VLC |
| `player/PlayerController.kt` | Queue and playback state |
| `ui/` | The Compose Desktop interface |

App data (yt-dlp.exe) is stored in `%LOCALAPPDATA%\ViTuneDesktop`.

## Troubleshooting

- **"VLC media player is needed"**: the build didn't bundle VLC (check the warning from
  `prepareBundledVlc`), and no installed VLC was found. Rebuild with `-PvlcDir`, or install
  the 64-bit VLC. The 32-bit version won't be found.
- **Songs fail to load**: usually YouTube changed something. Restart the app so yt-dlp
  updates, and make sure Deno (or Node.js) is installed.
- **Search fails**: YouTube may have changed its API; `git pull` in the ViTune folder
  picks up upstream fixes to the provider code.

Note: this app uses unofficial YouTube APIs, like ViTune itself, so it's intended for
personal use and can break when YouTube makes changes.
