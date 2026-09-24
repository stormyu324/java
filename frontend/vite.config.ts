import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// `npm run dev` proxies API calls to the Spring Boot backend on :8080.
// `npm run build` writes into the backend's static resources so one JAR serves everything.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
});
