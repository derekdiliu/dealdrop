import { type NextRequest, NextResponse } from "next/server";

import { ACCESS_COOKIE, REFRESH_COOKIE, refreshTokens, storeTokens } from "@/lib/session-tokens";

/**
 * Keeps people signed in. Once the short-lived access cookie has gone, the refresh token is swapped for
 * new tokens before the page renders: the page sees the new access token and the browser stores both.
 * Server components can't set cookies, so this can't happen later. Only a convenience: the backend checks
 * the token on every call.
 */
export async function proxy(request: NextRequest) {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (!refreshToken || request.cookies.has(ACCESS_COOKIE)) {
    return NextResponse.next();
  }
  const tokens = await refreshTokens(refreshToken);
  if (tokens === "unavailable") {
    // Signed out for this request only; the next one tries again.
    return NextResponse.next();
  }

  if (tokens) {
    request.cookies.set(ACCESS_COOKIE, tokens.accessToken);
    request.cookies.set(REFRESH_COOKIE, tokens.refreshToken);
  } else {
    request.cookies.delete(REFRESH_COOKIE);
  }
  const response = NextResponse.next({ request: { headers: request.headers } });
  if (tokens) {
    storeTokens(response.cookies, tokens);
  } else {
    response.cookies.delete(REFRESH_COOKIE);
  }
  return response;
}

export const config = {
  // Pages, server actions and route handlers; not static files.
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
