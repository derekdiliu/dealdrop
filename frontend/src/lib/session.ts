import "server-only";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

// The access token lives in an httpOnly cookie: page scripts can't read it, so an XSS bug can't
// steal it. Server code attaches it to API calls on the browser's behalf.
const COOKIE = "dealdrop_session";

export type SessionUser = { id: string; email: string };

type Claims = { sub: string; email: string };

export async function saveSession(accessToken: string, expiresInSeconds: number) {
  (await cookies()).set(COOKIE, accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: expiresInSeconds,
  });
}

export async function clearSession() {
  (await cookies()).delete(COOKIE);
}

/**
 * The current token, or null if signed out. Expiry isn't checked here: the cookie expires with the
 * token, and the backend verifies it on every call. (Reading the clock here would also stop Next.js
 * from prerendering the pages that use it.)
 */
export async function getToken(): Promise<string | null> {
  return (await cookies()).get(COOKIE)?.value ?? null;
}

export async function getUser(): Promise<SessionUser | null> {
  const token = await getToken();
  const claims = token ? decode(token) : null;
  return claims ? { id: claims.sub, email: claims.email } : null;
}

/** The token, or a redirect to sign in that brings the customer back to `returnTo` afterwards. */
export async function requireToken(returnTo: string): Promise<string> {
  const token = await getToken();
  if (!token) {
    redirect(`/login?next=${encodeURIComponent(returnTo)}`);
  }
  return token;
}

/** Reads the claims for display. Not a security check: the signature is verified by the backend. */
function decode(token: string): Claims | null {
  try {
    return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"));
  } catch {
    return null;
  }
}

/** Only same-site paths, so a crafted link can't send people elsewhere after signing in. */
export function safeReturnPath(path: unknown): string {
  return typeof path === "string" && path.startsWith("/") && !path.startsWith("//") ? path : "/";
}
