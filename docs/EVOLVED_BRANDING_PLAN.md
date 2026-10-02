# EvolveD Branding Plan

## Existing branding audit
The repository previously exposed a strong CS Launcher Plus identity in:
- app resource strings
- build metadata
- user-visible labels
- launcher home and about screens
- Firebase/default backend references

## Rebrand approach
The migration keeps the original functionality but updates the user-facing identity to:
- EvolveD Launcher
- EvolveD visual theme
- modern dark purple accent styling

## Package / application ID decision
The repository still uses the technical package and app IDs rooted in the existing project (`com.cslaucher.plus`) because changing the package namespace across the entire Android project would be a high-risk migration for a launcher with native + provider + storage + manifest wiring.

This means the rebrand was preserved in user-facing branding while avoiding risky, breaking package renames.

## Visual direction
The intended system uses:
- background: `#08060D`
- secondary surface: `#160B24`
- primary purple: `#8B5CF6`
- bright purple: `#A855F7`
- text: white / light gray

This style is applied as a design direction while keeping the existing UI framework intact.

## User-facing replacement targets
The rebrand should replace:
- app name and short name
- launcher UI text and dialogs
- home screen labels
- about page text
- promotional banners and welcome flows
- app metadata where appropriate

## Promotional removal
CS Client promotional UI and sponsor messaging were treated as content to remove from user-facing launch flows, while preserving the underlying launcher architecture.

## Legal attribution preservation
The project retains legal attribution and open-source notice requirements. The launcher keeps the original project provenance and license materials while rebranding the product identity.

## Final principle
EvolveD branding must be visible to users without breaking runtime, provider, storage, or gameplay functionality.
