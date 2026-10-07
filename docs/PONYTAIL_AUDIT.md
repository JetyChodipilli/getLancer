delete: Remove the sidebar module and its five exclusive helpers, which have no application consumer; current references are the sidebar's own imports and a test-only rendering fixture. Nothing replaces this unused UI; adjust that fixture to the retained workspace navigation while preserving the UI smoke suite. [components/ui/sidebar.tsx; components/ui/sheet.tsx; components/ui/separator.tsx; components/ui/skeleton.tsx; components/ui/tooltip.tsx; hooks/use-mobile.ts] (-983 lines).

delete: Remove the unused chart component and the `recharts` dependency, whose only application-source import is that component. Existing marketplace metrics render without it; preserve the UI smoke suite and adapt its chart-theme fixture to a retained component. [components/ui/chart.tsx; package.json] (-377 lines, -1 dependency).

net: -1360 lines, -1 deps possible.
