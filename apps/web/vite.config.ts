/// <reference types="vitest/config" />
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
      // selfDestroying: the generated service worker's only job is to
      // unregister itself and clear its caches on next visit -- it actively
      // cleans up any copy a browser already installed (found the hard way:
      // a stale SW kept serving an old cached build with no backend behind
      // it after `docker compose down`, long after the containers it was
      // talking to were gone). The manifest below is unaffected -- it still
      // satisfies "manifest mínimo" (spec §2) -- this only neutralizes the
      // asset-precaching behavior that made every `web` rebuild a trap
      // during active Fase 0/1 development. Revisit once a real offline/
      // business caching story is actually wanted (Fase 1+).
      selfDestroying: true,
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
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
  },
})
