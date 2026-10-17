import Link from "next/link";

import { SignOutButton } from "@/components/sign-out-button";
import { getUser } from "@/lib/session";

/** Reads the session cookie, so it must render inside a Suspense boundary. */
export async function AccountNav() {
  const user = await getUser();
  if (!user) {
    return (
      <Link href="/login" className="text-sm font-medium hover:underline">
        Sign in
      </Link>
    );
  }
  return (
    <nav className="flex items-center gap-4 text-sm">
      {user.isAdmin && (
        <Link href="/admin" className="font-medium hover:underline">
          Admin
        </Link>
      )}
      <Link href="/orders" className="font-medium hover:underline">
        My orders
      </Link>
      <span className="hidden text-zinc-500 sm:inline">{user.email}</span>
      <SignOutButton />
    </nav>
  );
}
