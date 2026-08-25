import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/** Keep browser requests on one origin; the Java gateway owns all receiver routing. */
export default defineConfig({
    plugins: [react()],
    server: {
        host: '127.0.0.1',
        port: 5173,
        strictPort: true,
        allowedHosts: ['localhost', '127.0.0.1'],
        cors: false,
        proxy: { '/api': { target: 'http://127.0.0.1:8781', changeOrigin: true } },
    },
    build: { target: 'es2023', sourcemap: true },
    test: {
        environment: 'node',
        include: ['src/**/*.test.{ts,tsx}'],
        restoreMocks: true,
    },
});
