import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// RTL auto-cleans between tests only when it detects a global `afterEach`
// (e.g. with `test.globals: true`). We don't enable globals, so it's wired
// explicitly here instead -- without it, every test's render() stays mounted
// and later tests in the same file see duplicate elements from earlier ones.
afterEach(() => {
  cleanup()
})
