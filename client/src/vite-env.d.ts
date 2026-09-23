interface ImportMetaEnv {
  /**
   * Optional base URL for backend API calls. When empty (default), requests
   * use relative paths routed through the Vite dev-server proxy; set to a
   * full origin to point the frontend at a different deployment.
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
