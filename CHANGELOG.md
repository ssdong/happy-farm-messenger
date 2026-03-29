# Changelog
All notable changes to "Happy Farm" will be documented in this file.

## [0.6.0] - 2026-03-28
### Changed
- Auto-scrolling to "New Messages" separator when user navigate to chat page for the first time.
- `FetchUnreadMessages` Logic: Automatically reconciles and receives missed messages after a WebSocket reconnection, specifically fixing stale data issues on mobile browsers when switching between apps. 