import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
import "@testing-library/jest-dom/vitest";

// RTL's automatic afterEach cleanup only self-registers when `afterEach` is a
// global (e.g. Jest, or Vitest with `test.globals: true`). This project keeps
// explicit imports from "vitest" instead, so cleanup is wired here.
afterEach(() => {
	cleanup();
});
