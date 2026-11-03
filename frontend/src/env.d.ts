/// <reference types="vite/client" />

/**
 * T171: compile-time types for the Vite environment and Vue single-file components.
 *
 * Vue SFC types are resolved natively by vue-tsc; the wildcard declaration below is the
 * fallback for tools that compile plain TypeScript without the Vue plugin. No business
 * logic, no defaults and no credentials belong in this file.
 */

interface ImportMetaEnv {
  /** Base URL of the backend REST API. Never carries credentials. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}
