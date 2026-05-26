<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Docker Registry Explorer Changelog
## [1.2.0] - 2026-05-26
### Added
- Lazy registry exploration that loads repositories and tags only when branches are expanded.
- Test Connection support in the add/edit registry dialog with validation-style success and failure feedback.
- Manifest information display for image tags, with support for Docker schema v2 and OCI manifest responses.
- Tag context-menu actions for showing manifest information and deleting images.

### Changed
- Registry requests and tag deletion now execute asynchronously to keep the IDE responsive.
- Registry management and loading feedback now use a focused single-panel tool-window UI.
- Image deletion now requires confirmation showing the selected image name and tag.

### Fixed
- Tool-window tree colors and selection repainting in newer Islands themes.
- Deprecated IntelliJ notification API usage.

## [1.1.2-stable] - 2026-04-13
### Added
- Support for 2026.1+ versions

## [1.1.0-stable] - 2023-01-02
### Added
- Support for 2023.2+ versions
- Fixed UI Freezes
- Implemented pagination for registries with more than 100 images

## [1.0.9-stable] - 2023-01-02
### Added
- Support for 2022.3+ versions

## [1.0.8-stable] - 2022-05-31
### Added
- Support for 2022.2 versions

## [1.0.7-stable] - 2021-10-05
### Added
- Support for self-signed certs

## [1.0.6-stable] - 2021-07-30
### Fixed
- Support for 2021.2 versions

## [1.0.5-stable] - 2021-06-18
### Fixed
- Compatibility issues

## [1.0.4-stable] - 2021-06-16
### Fixed
- Registry not found error

## [1.0.3-stable] - 2021-04-13
### Added
- Support for 2021.1 versions.

### Fixed
- Removed usages of deprecated APIs 

## [1.0.2-stable] - 2021-04-01
### Added
- Delete Docker image function

### Fixed
- Minor UI bugfixes

## [1.0.0-stable] - 2020-12-10
### Added
- Initial release
