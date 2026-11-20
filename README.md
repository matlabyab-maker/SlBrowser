# SlBrowser

A clean, independent Android browser starter project, written from scratch in Kotlin.
The UI is created programmatically; no image-based buttons or menu layouts are used.

## Current starter milestone
- English-only browser interface.
- Address/search field, Go, Tabs, Menu.
- Back, Forward, History, Bookmarks, Downloads controls.
- Large bottom actions: File, Answer, Save, Auto Select.
- WebView with JavaScript, DOM storage, cookies and zoom controls.
- Basic desktop/mobile user-agent switching.
- Save page text to a user-named TXT file through Android's document picker.
- Auto Select prototype for extracting text from a tapped page section.
- Multi-file document picker launch.
- Basic Answer helper that inserts text into a detected editable field; the user must review and press the website's Send button.
- Last visited URL, history and bookmarks persisted locally.
- GitHub Actions workflow builds on pushes to all branches and manual dispatch.

## Important implementation limits
This is the first project structure, not a finished production browser.
- Tabs currently show a single-tab placeholder; full multi-WebView tab management is a later milestone.
- File opens Android's multi-select picker, but attaching multiple selected URIs directly to arbitrary websites requires a more advanced WebChromeClient file chooser implementation and site testing.
- Answer cannot safely or reliably press Send on every AI website. Website-specific adapters and testing are required.
- Auto Select is a best-effort DOM text extractor. Complex nested widgets, cross-origin iframes, canvas content and closed shadow roots may restrict extraction.
- Google account sign-in depends on each website's supported web authentication flow, cookies and Google security policies; this project does not bypass Google restrictions.
- Download rename-before-start, pause/resume and per-download controls are planned for a later milestone.
- Android SDK 35 and JDK 17 are used by the build workflow.

## Build
Open the project in Android Studio, or run:

```bash
gradle assembleDebug
```

The debug APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`
