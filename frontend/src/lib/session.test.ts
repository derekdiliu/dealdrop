import { describe, expect, it, vi } from "vitest";

import { safeReturnPath } from "@/lib/session";

// The real module throws outside React's server environment.
vi.mock("server-only", () => ({}));

describe("safeReturnPath", () => {
  it.each(["/", "/orders", "/deals/abc/checkout?quantity=2#address"])("keeps %j", (path) => {
    expect(safeReturnPath(path)).toBe(path);
  });

  it.each([
    "https://evil.example",
    "//evil.example",
    // Browsers read a backslash as a slash and ignore tabs and newlines, making these "//evil.example".
    "/\\evil.example",
    "/\t/evil.example",
    "/\n/evil.example",
    "evil.example",
    "",
  ])("sends %j home instead", (path) => {
    expect(safeReturnPath(path)).toBe("/");
  });

  it("sends anything that isn't a string home", () => {
    expect(safeReturnPath(null)).toBe("/");
    expect(safeReturnPath(undefined)).toBe("/");
  });
});
