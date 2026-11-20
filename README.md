# SlBrowser — Version 1.4

Updates in this revision:
- Replaced the horizontally scrolling second row with a fixed-width row of equally sized controls that fit the screen: Back, Forward, Past, Full Screen, History, Bookmarks, Downloads, and Mode.
- Kept the horizontal page-progress indicator in a fixed 4dp slot so showing/hiding progress does not shift the home layout.
- Reordered the bottom controls so File is immediately left of Answer, and Answer is at the far right.
- Updated button labels to larger, top-left aligned text rather than centered text.
- Added pinch-to-zoom for the web page, clamped between 60% and 100%.
- Added Past (paste clipboard text into the currently focused web text field) and Full Screen controls.
- Added persistent settings to browse without images and to disable/enable JavaScript. Disabling JavaScript can prevent some sites from working correctly.
- Shortened History and Bookmark list labels to compact host/path text to avoid oversized URL strings; the original URL remains the navigation target.
- Preserved the selected launcher icon asset.

Build with the included GitHub Actions workflow. APK compilation was not run in this environment because Gradle is not installed.
