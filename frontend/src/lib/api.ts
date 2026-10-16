import "server-only";

// Server-side access to the backend through the gateway.

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

export function apiFetch(path: string, init: RequestInit & { token?: string | null } = {}) {
  const { token, headers, ...rest } = init;
  return fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: {
      ...(rest.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });
}

/** The human-readable message from an RFC 9457 problem response, or a generic one. */
export async function problemMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (typeof body.detail === "string") {
      return body.detail;
    }
  } catch {
    // Not a problem document.
  }
  return "Something went wrong. Please try again.";
}
