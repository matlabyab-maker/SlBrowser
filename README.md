# SlBrowser 1.7

## Video Links: yt-dlp extraction engine
The Video Links panel now offers an explicit “Extract links from this page (yt-dlp)” action. It analyzes the current HTTP/HTTPS page URL using yt-dlp-android, then offers a best-compatible video download. It requests separate best video + audio streams and MP4 merge where supported, using the bundled FFmpeg component.

Existing WebView-detected direct media candidates remain available in the same panel.

## Dependency and compatibility
- `io.github.junkfood02.youtubedl-android:library:0.18.1`
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`
- ABI: `armeabi-v7a`
- Minimum Android API: 24 (Android 7.0), increased from 23 because the extractor library requires API 24.
- Native libraries are packaged uncompressed/extracted as required by the dependency.

## Important legal and technical notes
The youtubedl-android project is GPL-3.0 licensed. Distributing SlBrowser with this dependency requires complying with GPL-3.0 obligations, including providing corresponding source and license notices for the distributed combined work. Review the license before public distribution.

yt-dlp supports many sites but cannot guarantee extraction from every site. DRM-protected streams, private/login-gated content, unsupported or changed site extractors, geo restrictions, and some live streams may fail. Download only content you are authorized to save.

## Build
Gradle dependencies are resolved from Maven Central. The project ZIP has not been compiled in this environment; validate through GitHub Actions before installing.


## Version 1.8 UI adjustment
- Reduced vertical padding at the bottom of the address/header row and removed extra vertical padding from the navigation row so the two top rows sit as close together as possible without changing button sizes.


## Version 1.9 build fixes
- Removed the unresolved `FFmpeg` Kotlin import and explicit initialization call. The FFmpeg Gradle dependency remains included for the extractor package.
- Replaced the invalid `WebView.isDestroyed` check with Activity lifecycle checks and a check that the WebView is still registered as a tab.
