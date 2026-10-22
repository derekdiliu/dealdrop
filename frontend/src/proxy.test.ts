import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

describe("proxy on deal pages", () => {
  // A fresh id per test, since the proxy remembers deals it has seen.
  let deal: string;
  beforeEach(() => {
    deal = crypto.randomUUID();
  });

  function dealPage(path: string, init: { method?: string; headers?: Record<string, string> } = {}) {
    return new NextRequest(`http://localhost:3100${path}`, init);
  }

  /** Stands in for the catalog (and identity, for refreshes) behind the gateway. */
  function backendAnswers(catalog: Response | Error) {
    const fetch = vi.fn((url: string) => {
      if (url.includes("/api/identity/refresh")) {
        return Promise.resolve(Response.json(newTokens));
      }
      return catalog instanceof Error ? Promise.reject(catalog) : Promise.resolve(catalog);
    });
    vi.stubGlobal("fetch", fetch);
    return fetch;
  }

  const rewrittenTo = (response: Response) => response.headers.get("x-middleware-rewrite");

  it("shows the not-found page, with a 404 status, for a deal the catalog doesn't have", async () => {
    for (const status of [404, 400]) {
      const fetch = backendAnswers(new Response(null, { status }));

      for (const path of [`/deals/${deal}`, `/deals/${deal}/checkout`]) {
        const response = await proxy(dealPage(path));
        expect(rewrittenTo(response)).toBe("http://localhost:3100/deal-not-found");
      }
      expect(fetch).toHaveBeenCalledWith(`http://localhost:8080/api/catalog/deals/${deal}`);
    }
  });

  it("renders the page as usual when the deal exists or the catalog can't answer", async () => {
    for (const answer of [new Response(null, { status: 503 }), new TypeError("fetch failed"), Response.json({ id: deal })]) {
      backendAnswers(answer);

      const response = await proxy(dealPage(`/deals/${deal}`));

      expect(rewrittenTo(response)).toBeNull();
    }
  });

  it("remembers a deal it has seen for a minute", async () => {
    vi.useFakeTimers();
    try {
      const fetch = backendAnswers(Response.json({ id: deal }));

      await proxy(dealPage(`/deals/${deal}`));
      await proxy(dealPage(`/deals/${deal}/checkout`));
      expect(fetch).toHaveBeenCalledTimes(1);

      vi.advanceTimersByTime(60_001);
      await proxy(dealPage(`/deals/${deal}`));
      expect(fetch).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("doesn't remember a missing deal", async () => {
    const fetch = backendAnswers(new Response(null, { status: 404 }));

    await proxy(dealPage(`/deals/${deal}`));
    await proxy(dealPage(`/deals/${deal}`));

    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("doesn't check on form posts", async () => {
    const fetch = backendAnswers(new Response(null, { status: 404 }));

    await proxy(dealPage(`/deals/${deal}/checkout`, { method: "POST" }));

    expect(fetch).not.toHaveBeenCalled();
  });

  it("still stores refreshed tokens when the deal is missing", async () => {
    backendAnswers(new Response(null, { status: 404 }));

    const response = await proxy(dealPage(`/deals/${deal}`, { headers: { cookie: "dealdrop_refresh=old-refresh" } }));

    expect(rewrittenTo(response)).toBe("http://localhost:3100/deal-not-found");
    expect(response.cookies.get("dealdrop_session")?.value).toBe("new-access");
    expect(response.cookies.get("dealdrop_refresh")?.value).toBe("new-refresh");
  });
});
