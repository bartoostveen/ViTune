# ✨ ViTune Desktop - Spotify-Like Features (Phase 1)

## New UI Layout

### Before (Minimal)
```
┌─────────────────────────────┐
│ Banner/Status               │
├─────────────────┬───────────┤
│                 │  Queue    │
│  Search Results │  Panel    │
│                 │  (360px)  │
├─────────────────┴───────────┤
│ Player Bar                  │
└─────────────────────────────┘
```

### After (Spotify-Like)
```
┌──────┬─────────────────────────┬───────────┐
│      │                         │           │
│ Side │  Main Content Area      │  Queue    │
│ bar  │ (Home/Search/Playlists) │  Panel    │
│ 250px│       Dynamic           │  360px    │
│      │                         │           │
├──────┴─────────────────────────┴───────────┤
│ Enhanced Player Bar with Controls           │
└─────────────────────────────────────────────┘
```

---

## 🎨 Visual Improvements

### Color Scheme (Spotify Green Theme)
- **Primary Green**: #1DB954 (Spotify's signature green)
- **Background**: #121212 (Dark, OLED-friendly)
- **Surface**: #121212 (Match background for depth)
- **Accent Text**: #B3B3B3 (Subtle secondary text)

All buttons and active states use the Spotify green accent for visual consistency.

---

## 📱 Sidebar Navigation

### Navigation Items
1. **🏠 Home** - Recently played, featured content, trending
2. **🔍 Search** - YouTube Music search across songs/artists/albums
3. **📚 Library** - Your music collection
4. **📋 Playlists** - View all playlists
5. **❤️ Liked Songs** - Your favorite tracks

### Playlist Management
- **Scrollable Playlist List**: Shows all your created playlists
- **+ Create Playlist Button**: Opens dialog to create new playlist
- **Click to View**: Select any playlist to see its songs
- **Right-click Options**: Edit, delete (coming soon)

---

## 🎵 Home Screen

### Features
- **Recently Played Section**: Shows last 10 songs you played
- **Song Cards**: 
  - Album artwork
  - Song title (2 lines max)
  - Artist name
  - Click to play

### Empty State
- Shows helpful message when no songs have been played yet

---

## ❤️ Liked Songs View

### Features
- **Heart Icon Header**: Large heart symbol with green highlight
- **Playlist Metadata**: 
  - "PLAYLIST" label
  - "Liked Songs" title
  - Total count of liked songs
- **All Your Favorites**: Chronologically sorted list of favorite tracks
- **Context Menu**: Play, Play Next, Add to Queue

---

## 📋 Playlist Details

### Features
- **Playlist Header**:
  - Album artwork (or default music note icon)
  - Playlist name
  - Description (if provided)
  - Song count
- **Full Song List**:
  - All songs in playlist
  - Right-click menu with play options
  - Current song highlighted in green
- **Empty State**: Message when no songs in playlist

---

## 🎮 Enhanced Player Controls

### Bottom Player Bar (All-in-One)

**Left Section**:
- Album artwork (60px)
- Song title (main text)
- Artist name (secondary text)
- ❤️ Favorite button (toggles like status)

**Center Section** (Transport Controls):
- 🔀 **Shuffle Button**: OFF (gray) → ON (green)
  - When OFF: Sequential play through queue
  - When ON: Queue randomized after current song
- ⏮️ **Previous**: Skip to previous song (if available)
- ▶️ **Play/Pause**: Large filled button in green
- ⏭️ **Next**: Skip to next song
- 🔁 **Repeat Button**: Three states with visual feedback
  - OFF (gray): Play through queue once
  - ALL (green): Loop entire queue
  - ONE (green + indicator): Repeat current song

**Right Section**:
- 🔊 Volume icon
- Volume slider (0-100%)

**Below Transport**:
- **Progress Slider**: 
  - Drag to seek
  - Shows current time and total duration
  - Gray when loading

---

## 📊 Shuffle & Repeat Modes

### Shuffle Toggle
```
Click Shuffle Button
    ↓
Shuffle: OFF → Shuffle: ON
    ↓
Icon changes from gray to green
Queue reordered (random after current)
```

### Repeat Cycle
```
Click Repeat Button (repeats)
    ↓
OFF → ALL → ONE → OFF → ...
    ↓
OFF: Play queue, stop at end
ALL: Loop back to start
ONE: Restart current song
```

---

## ➕ Create Playlist Dialog

### Features
- **Modal Dialog**: Overlays main window
- **Playlist Name** (required):
  - Text input
  - Must be non-empty
- **Description** (optional):
  - Multi-line text area
  - For organizational notes
- **Buttons**:
  - Cancel: Dismiss without saving
  - Create: Add playlist and close dialog

### After Creation
- New playlist appears in sidebar immediately
- Can add songs and edit later

---

## 🎯 New Keyboard Shortcuts (Built-in)

Currently Available:
- **Ctrl+P**: Play/Pause
- **Ctrl+→**: Next track
- **Ctrl+←**: Previous track

Future Shortcuts (Phase 2):
- **Ctrl+L**: Like/Unlike current song
- **Ctrl+Shift+S**: Toggle shuffle
- **Ctrl+Shift+R**: Cycle repeat mode
- **Ctrl+H**: Go to Home
- **Ctrl+F**: Focus search
- **Ctrl+N**: Create new playlist

---

## 📦 Data Persistence (Coming in Phase 2)

### What Gets Saved
- ✅ Playlists (name, description, songs)
- ✅ Liked songs (with timestamp)
- ✅ Play history (recently played, timestamps)
- ✅ Player state (position, volume)
- ✅ User preferences (theme, etc.)

### Storage Location
- **Windows**: `%LOCALAPPDATA%\ViTuneDesktop\`
- **Linux/Mac**: `~/.vitune-desktop/`

---

## 🔄 Next Phase (Phase 2) Features

1. **Database Integration**: Persistent storage using Room/SQLite
2. **Wire Favorites**: Saving/loading liked songs from disk
3. **History Tracking**: Automatic recently played population
4. **Playlist Editing**: Add/remove songs from playlists
5. **Advanced Search**: Filter by type (songs/artists/albums)
6. **Drag & Drop**: Reorder songs in queue and playlists

---

## 🎨 Design System

### Spacing
- Outer padding: 16dp
- Section gap: 16dp
- Item gap: 2-8dp
- Icon spacing: 12dp

### Typography
- Headlines: Material3 headlineSmall/Large
- Body: Material3 bodyLarge/Medium/Small
- Labels: Material3 labelMedium/Small
- Font Weight: Normal → SemiBold for active states

### Corners
- Buttons & Cards: 8dp rounded corners
- Dialogs: 12dp rounded corners
- Icons: 18-24dp sizes

### Hover States
- Rows: Light background (primary at 12% opacity)
- Buttons: Darker shade
- Disabled: Grayed out at 50% opacity

---

## 🚀 Getting Started

### Prerequisites
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install DenoLand.Deno  # or Node.js
```
VLC is bundled into the installer. To build that in, extract the 64-bit VLC 3.x zip to
`vlc-runtime\` at the repository root (see README.md); otherwise install VLC yourself.

### Build & Run
```powershell
cd desktop
./gradlew.bat run
```

### First Time
1. App shows setup screen (downloading yt-dlp)
2. Once ready, sidebar appears on left
3. Try:
   - Click "Home" tab
   - Click "Search" and search for a song
   - Click play - queue fills with related songs
   - Click Shuffle to randomize next songs
   - Click Repeat to cycle through modes

---

## 💾 File Organization

### New Files (Phase 1)
```
desktop/src/main/kotlin/app/vitune/desktop/
├── model/
│   ├── Playlist.kt (new)
│   └── AppState.kt (new)
├── ui/
│   ├── Sidebar.kt (new)
│   ├── HomePanel.kt (new)
│   ├── PlaylistPanel.kt (new)
│   ├── LikedSongsPanel.kt (new)
│   ├── PlaylistDialog.kt (new)
│   ├── App.kt (refactored)
│   ├── PlayerBar.kt (enhanced)
│   ├── Icons.kt (updated)
│   └── Theme.kt (updated)
└── player/
    └── PlayerController.kt (enhanced)
```

---

## 📝 Known Limitations (Phase 1)

- ❌ Favorites not yet persisted to disk
- ❌ Recently played not auto-populated
- ❌ No database/file storage (in progress)
- ❌ Playlist editing UI (add/remove songs) not yet built
- ❌ No artist/album detail pages yet
- ❌ No drag-and-drop reordering
- ⚠️ Shuffle mode visual only (doesn't affect queue yet)

These will be completed in Phase 2!

---

**Status**: Phase 1 Complete ✅ → Phase 2 In Progress 🔄

Last Updated: 2026-11-14
