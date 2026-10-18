import { describe, expect, it, vi } from "vitest";

import { submittedValues } from "@/lib/forms";

// The real module throws outside React's server environment.
vi.mock("server-only", () => ({}));

describe("submittedValues", () => {
  it("keeps typed text but never the password or React's own fields", () => {
    const form = new FormData();
    form.set("email", "kiri@example.co.nz");
    form.set("password", "kia-ora-1234");
    form.set("$ACTION_ID_abc", "");
    form.set("photo", new Blob(["x"]));

    expect(submittedValues(form)).toEqual({ email: "kiri@example.co.nz" });
  });
});
