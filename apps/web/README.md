# web

React 19 + TypeScript + Vite PWA for Money Pulse. See the repo root `CLAUDE.md`
for commands and `docs/PROJECT.md` / `specs/phase-0.md` for context — this
package has no documentation of its own beyond this file.

- `npm run dev` — local dev server (proxies `/transactions` to
  `http://localhost:8080`, overridable via `VITE_API_PROXY_TARGET`).
- `npm run build` — type-checks and builds to `dist/`.
- `npm run preview` — serves the production build (this is what the
  `web` service in the repo's `compose.yaml` runs).
