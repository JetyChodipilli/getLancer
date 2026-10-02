# Authentication layout refinement

The supplied login/signup screenshots showed a warm orange wash behind the artwork, a pale wordmark, a narrow form and small supporting text. Large outside gutters and inconsistent inner padding made the page feel undersized.

- Keep all six artwork assets, their proportions and the existing slow orbit.
- Replace the story wash with white and cool silver (#EEF0F4); use #F4F5F7 around the page.
- Use the supplied charcoal/orange vector logo in the shared brand component: 240px on desktop authentication, 190px on phones, 180px in desktop navigation.
- Balance the desktop columns and reduce outside gutters to 12–24px. Give the form a 520px maximum width.
- Use 54px fields and submit buttons, 52px provider buttons, 17px input text, 15px labels and at least 14px supporting text.
- Keep 16px field spacing, 44px password visibility targets and wrapping account options. Compact the preview service notice into two columns only when sufficient width is available.
- Reduce the artwork canvas on short laptops, preserving form readability. Allow natural vertical scrolling for signup and service messages. Hide decorative artwork on phones.

Verification covers login and signup at 1440×1000, 1366×768, 1024×768, 768×1024, 720×900, 375×812 and 320×812, including loaded logo assets, no horizontal overflow, readable controls, password visibility, and reachable submit buttons. Existing tests also cover provider behavior, navigation and animation pause behavior.

The published demo continues to disclose unavailable account services. Styling does not activate an unconfigured backend or OAuth provider.
