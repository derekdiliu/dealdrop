import { type NextRequest, NextResponse } from "next/server";

import { ACCESS_COOKIE, REFRESH_COOKIE, refreshTokens, storeTokens, type Tokens } from "@/lib/session-tokens";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

// Matches no route, so rendering it gives the not-found page with a real 404 status.
const DEAL_NOT_FOUND_PATH = "/deal-not-found";
const DEAL_PAGE = /^\/deals\/([^/]+)(?:\/checkout)?\/?$/;

// Deals the catalog has confirmed recently, so prefetches (the home page prefetches every deal it lists,
// and the proxy can't tell a prefetch apart) and repeat visits don't each cost a lookup. Published deals
// can't change; one that ends within this time gets the page's own not-found instead (200 + noindex).
// Missing ids aren't remembered, so requests for random ids can't grow the map.
const KNOWN_DEAL_MS = 60_000;
const knownDeals = new Map<string, number>();

export async function proxy(request: NextRequest) {
  const tokens = await refreshSession(request);
  // Only when the cookies changed does the page need to see them.
  const init = tokens === undefined ? undefined : { request: { headers: request.headers } };
  const response = (await isMissingDeal(request))
    ? NextResponse.rewrite(new URL(DEAL_NOT_FOUND_PATH, request.url), init)
    : NextResponse.next(init);

  if (tokens) {
    storeTokens(response.cookies, tokens);
  } else if (tokens === null) {
    response.cookies.delete(REFRESH_COOKIE);
  }
  return response;
}

/**
 * Keeps people signed in. Once the short-lived access cookie has gone, the refresh token is swapped for
 * new tokens before the page renders: the page sees the new access token and the browser stores both.
 * Server components can't set cookies, so this can't happen later. Only a convenience: the backend checks
 * the token on every call.
 *
 * Returns the new tokens, null if the visitor is now signed out, or undefined if nothing changed.
 */
async function refreshSession(request: NextRequest): Promise<Tokens | null | undefined> {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (!refreshToken || request.cookies.has(ACCESS_COOKIE)) {
    return undefined;
  }
  const tokens = await refreshTokens(refreshToken);
  if (tokens === "unavailable") {
    // Signed out for this request only; the next one tries again.
    return undefined;
  }

  if (tokens) {
    request.cookies.set(ACCESS_COOKIE, tokens.accessToken);
    request.cookies.set(REFRESH_COOKIE, tokens.refreshToken);
  } else {
    request.cookies.delete(REFRESH_COOKIE);
  }
  return tokens;
}

/**
 * A deal page streams its content after the 200 status has been sent, so a missing deal can't turn it
 * into a 404 there; checking here first gives crawlers and link checkers a real one. Form posts (the
 * checkout's server action) aren't checked. If the catalog can't answer, the page renders and handles it.
 */
async function isMissingDeal(request: NextRequest): Promise<boolean> {
  const id = DEAL_PAGE.exec(request.nextUrl.pathname)?.[1];
  if (!id || request.method !== "GET") {
    return false;
  }
  const knownUntil = knownDeals.get(id);
  if (knownUntil !== undefined) {
    if (knownUntil > Date.now()) {
      return false;
    }
    knownDeals.delete(id);
  }
  try {
    const response = await fetch(`${API_BASE_URL}/api/catalog/deals/${encodeURIComponent(id)}`);
    if (response.ok) {
      knownDeals.set(id, Date.now() + KNOWN_DEAL_MS);
    }
    // 400 means the id is not a valid UUID, which is just another unknown deal.
    return response.status === 404 || response.status === 400;
  } catch {
    return false;
  }
}

export const config = {
  // Pages, server actions and route handlers; not static files.
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
