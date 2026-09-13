# Sol-1

**Sol-1 stands for day one.** It is a sobriety and habit tracker that puts your streak where you will see it a hundred times a day: your phone's lock screen.

Live at **[sol-1.netlify.app](https://sol-1.netlify.app)**. Built for the Pixel 10, works on any phone that can set a wallpaper.

---

## What it does

- **Days since.** Start a counter from the moment you decided: last drink, last cigarette, last anything. It counts up in days, hours and minutes.
- **Daily habits.** Gym, reading, meditation. Tap *Check in* each day; miss a day and the streak restarts, but your history is kept.
- **I slipped.** A reset is logged, not hidden. Your longest streak, total days and number of resets stay on the card. Day one starts again from right now.
- **Milestones.** 1, 3, 7, 14, 30, 60, 90, 180, 365 days and beyond, with a progress bar toward the next one.
- **Lock screen wallpaper.** Renders your trackers into a 1080 × 2424 image (the Pixel 10's exact resolution) with the number placed below the clock zone. Six themes, optional start date, milestone bar, a 28-day dot grid for habits, and a daily line at the bottom.
- **Private by design.** Nothing leaves your phone. Data lives in the browser's local storage, with JSON export and import for backups.
- **Installable.** Add it to your home screen and it opens like an app and works offline.

## Setting your lock screen on a Pixel

1. Open [sol-1.netlify.app](https://sol-1.netlify.app) and add a tracker.
2. Tap **Share image** and pick **Photos** (or tap **Download PNG**, which lands in Files → Downloads).
3. Long-press an empty spot on the home screen → **Wallpaper & style** → **Lock screen** → **More wallpapers** → **My photos**, and pick the image.
4. Apply it to the **Lock screen only** and skip any effects or zoom so the number stays where it was designed to sit.
5. The wallpaper is a still image. When you want the count to refresh, open Sol-1 and share or download again. Two taps a day makes a good check-in ritual.

In Chrome, tap ⋮ → **Add to Home screen** (or the Install button in the app) so it launches like an app.

## Why a wallpaper and not a widget

Android 16 QPR2 brought lock screen widgets back to Pixel phones, but a widget has to come from a native Android app. A web page cannot be one. A wallpaper is the next best thing: it needs no app store, no permissions, and no account, and it is on screen every time you pick up the phone. If a self-updating widget ever matters more than that simplicity, the rendering logic in `app.js` is the starting point for a native version.

## Files

| File | Purpose |
|---|---|
| `index.html` | The app: tracker cards, lock screen preview and options, instructions, backup |
| `app.js` | State, streak maths, the canvas wallpaper renderer, share and download |
| `manifest.webmanifest` | Installable web app metadata |
| `sw.js` | Service worker so the app loads offline |
| `icon.svg`, `icon-*.png` | App icons (the PNGs are rendered from the SVG) |

No build step. Netlify deploys the repository root as static files on every push to `master`.

## Running locally

Any static server works. For example:

```bash
python3 -m http.server 8000
```

Then open http://localhost:8000. The service worker only registers on HTTPS, so local runs simply skip offline caching.

## Notes

- The display numerals use the Inter Tight font from Google Fonts and fall back to Roboto or the system font if it cannot load.
- Clearing site data erases your trackers. Use **Export backup** in the app if the streak matters to you.
