# Authentication artwork and compact form

The approved sample retains the original chrome ring, bubbles, coding laptop and four glass cards. The implementation uses those exact existing image files, with larger artwork across the left panel and a compact account form on the right.

## Layout and motion

- Keep 65:35 columns above 1000px, 55:45 on tablets and one compact column on phones.
- Use a panel-width art scene, up to 1160px, with a 1.6 aspect ratio and 16px safety margins at the panel edges. Avoid shrinking the art according to viewport height.
- Keep the laptop stationary at 56% of scene width. Four upright cards follow a closed 72-second ellipse with quarter-turn spacing; their visible silhouettes remain separated and contained.
- Keep rotation active while users type. Remove the pause/resume button. Honor OS reduced motion and suspend unseen work when hidden or offscreen.
- Keep story copy in normal flow; short windows can scroll naturally. Center the compact form within the viewport independently of longer story content.
- Preserve the exact supplied charcoal/orange logo, neutral silver background and complete frame asset.
- Retain the 320px maximum form, 26px heading, 14px labels, 16px input text, 44px controls and grouped provider buttons.

## Verification

Login and signup are checked at 1920x1080, 1440x1000, 1366x768, 1366x650, 1024x768, 768x1024, 720x900, 375x812 and 320x812. Browser checks verify scene width, complete asset bounds, form dimensions, clear supplied logo, no horizontal overflow, password visibility and reachable actions. A full virtual orbit independently measures image alpha silhouettes, confirming visible card separation and a stationary laptop. A focused-field journey proves rotation continues during form use.

The existing account-service and provider behavior remains unchanged. The published preview continues to disclose unavailable account services. No asset files, dependencies, credentials or security policies are changed.

The earlier layout research reviewed Webflow's login and split-screen example for compact account hierarchy. The approved user sample determines the final artwork composition.
