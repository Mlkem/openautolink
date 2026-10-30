# Changelog

This file tracks repository-level OpenAutoLink changes.

## Current source state

### Car app
- Application ID default: `com.openautolink.app`
- Version name: `0.1.0`
- Version code: `1`
- Minimum SDK: 32
- Target/compile SDK: 36

### Companion app
- Application ID: `com.openautolink.companion`
- Version name: `1.0.0`
- Version code: `1`
- Minimum SDK: 26
- Target/compile SDK: 36

## Release infrastructure

- CI workflow present.
- Companion build workflow present.
- Native dependency build workflow present.
- Release workflow present and configured to attach signed car and companion APKs to GitHub Releases.
- Release signing material is expected through GitHub repository secrets and is not committed.

## Published releases

No GitHub Releases are currently published in this repository.

Historical release notes will be added here when verified tags/build artifacts are available. Version history should not be reconstructed by guesswork.
