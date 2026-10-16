import "server-only";

import { apiFetch } from "@/lib/api";
import { getToken } from "@/lib/session";

/**
 * Forwards a browser request to the backend with the session token attached, so client components
 * can call the API without ever handling the token themselves.
 */
export async function forwardWithSession(path: string, method: "GET" | "POST"): Promise<Response> {
  const token = await getToken();
  if (!token) {
    return Response.json({ detail: "Please sign in again." }, { status: 401 });
  }
  const response = await apiFetch(path, { method, token });
  return new Response(response.body, {
    status: response.status,
    headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" },
  });
}
