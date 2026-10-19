"use server";

import { apiFetch, problemMessage } from "@/lib/api";
import { submittedValues } from "@/lib/forms";
import { clearSession, getRefreshToken, safeReturnPath, saveSession } from "@/lib/session";

/**
 * Sign-in and sign-out return where to go rather than redirecting, and the browser then does a full
 * page load: when the user changes, nothing the client cached for the previous identity is reused.
 */
export type FormState = {
  error?: string;
  redirectTo?: string;
  // React resets a form after its action runs, so a failed submission hands back what was typed.
  values?: Record<string, string>;
};

export async function signIn(_state: FormState, form: FormData): Promise<FormState> {
  const response = await apiFetch("/api/identity/login", {
    method: "POST",
    body: JSON.stringify({ email: form.get("email"), password: form.get("password") }),
  });
  if (!response.ok) {
    return { error: await problemMessage(response), values: submittedValues(form) };
  }
  await saveSession(await response.json());
  return { redirectTo: safeReturnPath(form.get("next")) };
}

export async function register(_state: FormState, form: FormData): Promise<FormState> {
  const email = form.get("email");
  const password = form.get("password");
  const created = await apiFetch("/api/identity/register", {
    method: "POST",
    body: JSON.stringify({ email, password, displayName: form.get("displayName") }),
  });
  if (!created.ok) {
    return {
      error: created.status === 400
        ? "Check your details: a valid email, and a password of 8 to 72 characters."
        : await problemMessage(created),
      values: submittedValues(form),
    };
  }
  return signIn({}, form);
}

/** Ends the sign-in on the server too, so the refresh token can't be used again. */
export async function signOut() {
  const refreshToken = await getRefreshToken();
  if (refreshToken) {
    // The cookies are cleared whether or not identity could be reached.
    await apiFetch("/api/identity/logout", { method: "POST", body: JSON.stringify({ refreshToken }) }).catch(() => null);
  }
  await clearSession();
}
