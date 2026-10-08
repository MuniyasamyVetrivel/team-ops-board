/// <reference types="vitest/config" />
import path from 'node:path';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, 'src') },
  },
  build: {
    rolldownOptions: {
      output: {
        // Long-lived vendor chunks: they change far less often than app code, so browsers keep them cached.
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|scheduler)[\\/]/, priority: 30 },
            { name: 'router', test: /node_modules[\\/]react-router[\\/]/, priority: 30 },
            { name: 'ui', test: /node_modules[\\/](@radix-ui|@floating-ui|lucide-react|sonner)[\\/]/, priority: 20 },
            { name: 'data', test: /node_modules[\\/](@tanstack|axios)[\\/]/, priority: 20 },
            { name: 'forms', test: /node_modules[\\/](react-hook-form|@hookform|zod)[\\/]/, priority: 20 },
            // Recharts and its own dependencies: only loaded by pages with charts.
            {
              name: 'charts',
              test: /node_modules[\\/](recharts|victory-vendor|d3-[^\\/]+|internmap|@reduxjs|react-redux|redux|redux-thunk|immer|reselect|es-toolkit|decimal\.js-light|eventemitter3|tiny-invariant)[\\/]/,
              priority: 20,
            },
            // react-markdown and its unified/remark pipeline: only loaded by knowledge base pages.
            {
              name: 'markdown',
              test: /node_modules[\\/](react-markdown|unified|bail|trough|devlop|vfile[^\\/]*|unist-[^\\/]+|mdast-[^\\/]+|hast-[^\\/]+|micromark[^\\/]*|remark-[^\\/]+|property-information|space-separated-tokens|comma-separated-tokens|html-url-attributes|decode-named-character-reference|character-entities[^\\/]*|zwitch|longest-streak|ccount|trim-lines|estree-util-[^\\/]+|style-to-js|style-to-object|inline-style-parser|is-plain-obj|extend|@ungap)[\\/]/,
              priority: 20,
            },
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    // Same-origin API calls in development: the browser talks to :5173 and Vite forwards /api to Spring Boot.
    // This keeps the httpOnly refresh cookie first-party and avoids CORS in dev.
    proxy: {
      '/api': { target: 'http://localhost:8080' },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
