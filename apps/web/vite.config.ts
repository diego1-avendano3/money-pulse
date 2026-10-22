import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// Local dev server (`npm run dev`) talks to transaction-api on the host's
// published port; inside compose it talks to the service by its Docker
// network name (set via VITE_API_PROXY_TARGET, see compose.yaml). Same
// local-default-with-env-override pattern as the Java apps' application.yml.
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // Spec §2 (scope): manifest mínimo only -- no offline/business caching
      // strategy for Phase 0, that's Phase 1+. generateSW with the default
      // precache-the-build-output behavior is what makes the app installable
      // at all; nothing beyond that is configured here.
      registerType: 'autoUpdate',
      manifest: {
        name: 'Money Pulse',
        short_name: 'Money Pulse',
        description: 'Real-time personal finance tracker',
        theme_color: '#0f172a',
        background_color: '#0f172a',
        display: 'standalone',
        icons: [
          // Placeholder: the template's own favicon, as an SVG with "any"
          // size (modern browsers accept this, so no PNG generation step is
          // needed for a Phase-0 placeholder). Replace with a real app icon
          // once the product has one.
          {
            src: 'favicon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/transactions': apiProxyTarget,
    },
  },
  preview: {
    proxy: {
      '/transactions': apiProxyTarget,
    },
  },
})
