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
