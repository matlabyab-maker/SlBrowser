# SlBrowser

Android WebView browser project.

## Version 1.1 updates
- Functional tab manager: create, switch, and close tabs; website pop-up windows open in a new tab.
- Website file upload bridge: the File button activates the current page's file input and returns selected device files to the website.
- Answer button attempts to activate the current website's Send/Submit control for the active or populated message editor; it no longer opens a separate message dialog.
- Downloads button lists Download Manager items and opens Android's downloads screen.
- Browser history, bookmarks, navigation, page text saving, desktop/mobile mode, zoom, and Auto Select are retained.
- Launcher icon uses the selected blue globe/yellow orbit/cursor design (PNG asset is 44,191 bytes, below 50 KB).

## Build
The GitHub Actions workflow builds a debug APK on pushes to any branch and supports manual runs.

## Version 1.2 bug fixes
- Auto Select OFF now removes the page selection-mode class as well as its visual style, preventing ordinary page taps from opening the save-text dialog.
- File opens Android's document picker even when the current website has no upload field; when a website file input exists, the selected URI is passed back to the website's upload control.
- File chooser falls back to ACTION_GET_CONTENT if ACTION_OPEN_DOCUMENT is unavailable.
