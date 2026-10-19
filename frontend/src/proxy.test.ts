import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";

import { proxy } from "@/proxy";

const newTokens = { accessToken: "new-access", expiresIn: 900, refreshToken: "new-refresh", refreshExpiresIn: 2592000 };

function request(cookies: Record<string, string>): NextRequest {
  const cookie = Object.entries(cookies).map(([name, value]) => `${name}=${value}`).join("; ");
  return new NextRequest("http://localhost:3100/orders", { headers: { cookie } });
}

/** Stands in for identity's refresh endpoint. */
function identityAnswers(answer: Response | Error) {
  const fetch = vi.fn(() => (answer instanceof Error ? Promise.reject(answer) : Promise.resolve(answer)));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("proxy", () => {
  it("leaves a current session and signed-out visitors alone", async () => {
    const fetch = identityAnswers(Response.json(newTokens));

    await proxy(request({ dealdrop_session: "access", dealdrop_refresh: "refresh" }));
    await proxy(request({}));

    expect(fetch).not.toHaveBeenCalled();
  });

  it("swaps an expired session for new tokens, for this page and the browser", async () => {
    const fetch = identityAnswers(Response.json(newTokens));

    const response = await proxy(request({ dealdrop_refresh: "old-refresh" }));

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/identity/refresh",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ refreshToken: "old-refresh" }) }),
    );
    expect(response.cookies.get("dealdrop_session")).toMatchObject({ value: "new-access", httpOnly: true, maxAge: 840 });
    expect(response.cookies.get("dealdrop_refresh")).toMatchObject({ value: "new-refresh", httpOnly: true, maxAge: 2592000 });
    // The page renders with the new access token, not as signed out.
    expect(response.headers.get("x-middleware-request-cookie")).toContain("dealdrop_session=new-access");
  });

  it("signs out when the refresh token is refused", async () => {
    identityAnswers(Response.json({ detail: "Please sign in again." }, { status: 401 }));

    const response = await proxy(request({ dealdrop_refresh: "revoked" }));

    expect(response.cookies.get("dealdrop_refresh")?.value).toBe("");
    expect(response.cookies.get("dealdrop_session")).toBeUndefined();
  });

  it("keeps the refresh token when identity can't be reached", async () => {
    identityAnswers(new TypeError("fetch failed"));

    const response = await proxy(request({ dealdrop_refresh: "refresh" }));

    expect(response.headers.get("set-cookie")).toBeNull();
  });
});
