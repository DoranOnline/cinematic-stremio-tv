# Cinematic TV Design System

## Product feeling

Cinematic should feel like a premium streaming service that is brighter, calmer,
and easier to navigate than stock Stremio. Every important action must be obvious
from sofa distance and remain comfortable on a touch tablet.

## Visual thesis

Warm cinema light over deep blue-black surfaces: large artwork, restrained glass,
high-contrast typography, and ember red reserved for playback, progress, and focus.

## Tokens

- Canvas: `#0A0E16`
- Surface: `#151B27`
- Raised surface: `#202938`
- Warm text: `#FFFAF1`
- Muted text: `#BFC8D7`
- Action red: `#E50914`
- Highlight amber: `#FFBF5B`
- Hairline: `rgba(255,255,255,.12)`
- Typography: Plus Jakarta Sans, bundled locally
- Radius: 12px controls, 16px panels, pill only for compact chips/actions

## Interaction rules

- Remote focus is always a white outline plus a red outer ring.
- Primary playback actions are central and visually larger than settings.
- Back dismisses the current layer before leaving a page.
- Touch targets are at least 52px on tablet layouts.
- Subtitles stay off until the viewer explicitly enables them.
- Motion is disabled in the native TV shell to protect low-power streamers.

## Screen hierarchy

- Home: navigation, editorial content rows, visible progress.
- Details: readable information zone on the left, artwork on the right, episodes
  and sources in one raised panel.
- Discover/Search/Library: filters on a single raised toolbar and consistent cards.
- Settings/Add-ons: grouped raised sections rather than unstructured lists.
- Player: title and exit on top, playback in the center, timeline and secondary
  settings at the bottom.
