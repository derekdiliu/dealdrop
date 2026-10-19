import "server-only";

import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";

import { ACCESS_COOKIE, REFRESH_COOKIE, storeTokens, type Tokens } from "@/lib/session-tokens";

// The tokens live in httpOnly cookies (see session-tokens.ts). Server code attaches the access token to
// API calls on the browser's behalf, and the proxy refreshes it when its cookie runs out.

export type SessionUser = { id: string; email: string; isAdmin: boolean };

type Claims = { sub: string; email: string; roles?: string[] };

export async function saveSession(tokens: Tokens) {
  storeTokens(await cookies(), tokens);
}

export async function clearSession() {
  const jar = await cookies();
  jar.delete(ACCESS_COOKIE);
  jar.delete(REFRESH_COOKIE);
}

export async function getRefreshToken(): Promise<string | null> {
  return (await cookies()).get(REFRESH_COOKIE)?.value ?? null;
}

/**
 * The current access token, or null if signed out. Expiry isn't checked here: the cookie expires before
 * the token, and the backend verifies it on every call. (Reading the clock here would also stop Next.js
 * from prerendering the pages that use it.)
 */
export async function getToken(): Promise<string | null> {
  return (await cookies()).get(ACCESS_COOKIE)?.value ?? null;
}

export async function getUser(): Promise<SessionUser | null> {
  const token = await getToken();
  const claims = token ? decode(token) : null;
  return claims ? { id: claims.sub, email: claims.email, isAdmin: isAdmin(claims) } : null;
}

/** The token, or a redirect to sign in that brings the customer back to `returnTo` afterwards. */
export async function requireToken(returnTo: string): Promise<string> {
  const token = await getToken();
  if (!token) {
    redirect(`/login?next=${encodeURIComponent(returnTo)}`);
  }
  return token;
}

/**
 * Like requireToken, but anyone who isn't an admin gets a not-found page. This only hides the back
 * office: the backend checks the role itself on every admin call.
 */
export async function requireAdminToken(returnTo: string): Promise<string> {
  const token = await requireToken(returnTo);
  const claims = decode(token);
  if (!claims || !isAdmin(claims)) {
    notFound();
  }
  return token;
}

function isAdmin(claims: Claims): boolean {
  return claims.roles?.includes("ADMIN") ?? false;
}

/** Reads the claims for display. Not a security check: the signature is verified by the backend. */
function decode(token: string): Claims | null {
  try {
    return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"));
  } catch {
    return null;
  }
}

/**
 * Only same-site paths, so a crafted link can't send people elsewhere after signing in. The path is
 * resolved the way a browser would, which also catches tricks like "/\evil.example" that browsers
 * read as "//evil.example".
 */
export function safeReturnPath(path: unknown): string {
  if (typeof path !== "string" || !path.startsWith("/")) {
    return "/";
  }
  const base = "http://same-site.invalid";
  const url = new URL(path, base);
  return url.origin === base ? url.pathname + url.search + url.hash : "/";
}
