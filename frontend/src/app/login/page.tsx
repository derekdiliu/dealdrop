import { Suspense } from "react";

import { SignInForm } from "@/components/auth-forms";

export default function Page() {
  return (
    <main className="mx-auto w-full max-w-sm flex-1 px-4 py-12">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight">Sign in</h1>
      {/* Reads ?next= from the URL, which is only known at request time. */}
      <Suspense>
        <SignInForm />
      </Suspense>
    </main>
  );
}
