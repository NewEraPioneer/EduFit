import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  test: {
    environment: 'node',
    globals: true,
    testTimeout: 60000,
    hookTimeout: 60000,
    include: ['tests/e2e/specs/**/*.e2e.test.ts'],
    fileParallelism: false, // Chạy tuần tự kịch bản E2E chống xung đột phiên trình duyệt
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '../../src'),
    },
  },
});
