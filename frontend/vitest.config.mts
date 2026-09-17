import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./test/setup.ts"],
    restoreMocks: true,
    coverage: {
      provider: "v8",
      include: ["api/**/*.ts", "components/**/*.tsx", "features/**/*.{ts,tsx}"],
      exclude: ["api/schema.d.ts"],
      reporter: ["text", "lcov"],
    },
  },
});
