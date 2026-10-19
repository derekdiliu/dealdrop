// Session cookies and token refresh, shared by server actions and the proxy. No "server-only" import
// here: the proxy can't load it.

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

export const ACCESS_COOKIE = "dealdrop_session";
export const REFRESH_COOKIE = "dealdrop_refresh";

// The access cookie goes a minute before the token expires, so the proxy swaps it while it still works.
const ACCESS_COOKIE_EARLY_SECONDS = 60;

/** What identity returns when signing in and when refreshing. */
export type Tokens = { accessToken: string; expiresIn: number; refreshToken: string; refreshExpiresIn: number };

type CookieOptions = { httpOnly: boolean; secure: boolean; sameSite: "lax"; path: string; maxAge: number };

/** Both tokens live in httpOnly cookies, so page scripts can't read them and an XSS bug can't steal them. */
export function storeTokens(cookies: { set(name: string, value: string, options: CookieOptions): unknown }, tokens: Tokens) {
  cookies.set(ACCESS_COOKIE, tokens.accessToken, cookieOptions(tokens.expiresIn - ACCESS_COOKIE_EARLY_SECONDS));
  cookies.set(REFRESH_COOKIE, tokens.refreshToken, cookieOptions(tokens.refreshExpiresIn));
}

function cookieOptions(maxAge: number): CookieOptions {
  return { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge };
}

/**
 * Swaps a refresh token for new tokens. Null if identity refused it (expired, revoked or reused), so the
 * visitor is signed out; "unavailable" if identity couldn't answer, so the next request can try again.
 */
export async function refreshTokens(refreshToken: string): Promise<Tokens | null | "unavailable"> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/identity/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (response.status === 401 || response.status === 400) {
      return null;
    }
    return response.ok ? await response.json() : "unavailable";
  } catch {
    return "unavailable";
  }
}
