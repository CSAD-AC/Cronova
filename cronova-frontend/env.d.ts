/// <reference types="vite/client" />

/* eslint-disable @typescript-eslint/no-empty-object-type --
   这是 Vue 官方的 *.vue 模块声明样板，泛型占位符保持原样最安全 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
