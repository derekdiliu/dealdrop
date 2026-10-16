"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useActionState, useEffect } from "react";

import { register, signIn } from "@/app/actions/auth";
import { Field, FormError, SubmitButton } from "@/components/form";

/** A full page load after signing in, so nothing cached while signed out is reused. */
function useRedirectAfterSignIn(redirectTo: string | undefined) {
  useEffect(() => {
    if (redirectTo) {
      window.location.assign(redirectTo);
    }
  }, [redirectTo]);
}

export function SignInForm() {
  const next = useSearchParams().get("next") ?? "/";
  const [state, action] = useActionState(signIn, {});
  useRedirectAfterSignIn(state.redirectTo);
  return (
    <form action={action} className="space-y-4">
      <input type="hidden" name="next" value={next} />
      <Field label="Email" name="email" type="email" autoComplete="email" required defaultValue={state.values?.email} />
      <Field label="Password" name="password" type="password" autoComplete="current-password" required />
      <FormError message={state.error} />
      <SubmitButton pendingText="Signing in…">Sign in</SubmitButton>
      <p className="text-center text-sm text-zinc-500">
        New here?{" "}
        <Link href={`/register?next=${encodeURIComponent(next)}`} className="underline">
          Create an account
        </Link>
      </p>
    </form>
  );
}

export function RegisterForm() {
  const next = useSearchParams().get("next") ?? "/";
  const [state, action] = useActionState(register, {});
  useRedirectAfterSignIn(state.redirectTo);
  return (
    <form action={action} className="space-y-4">
      <input type="hidden" name="next" value={next} />
      <Field label="Name" name="displayName" autoComplete="name" required maxLength={100} defaultValue={state.values?.displayName} />
      <Field label="Email" name="email" type="email" autoComplete="email" required defaultValue={state.values?.email} />
      <Field label="Password" name="password" type="password" autoComplete="new-password" required minLength={8} maxLength={72} />
      <FormError message={state.error} />
      <SubmitButton pendingText="Creating account…">Create account</SubmitButton>
      <p className="text-center text-sm text-zinc-500">
        Already have an account?{" "}
        <Link href={`/login?next=${encodeURIComponent(next)}`} className="underline">
          Sign in
        </Link>
      </p>
    </form>
  );
}
