"use client";

import { signOut } from "@/app/actions/auth";

export function SignOutButton() {
  return (
    <form
      action={async () => {
        await signOut();
        // A full page load on purpose, so pages cached for the signed-in user can't be shown again.
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign("/");
      }}
    >
      <button type="submit" className="text-zinc-500 hover:underline">
        Sign out
      </button>
    </form>
  );
}
