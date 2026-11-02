// Shared class names. Kept out of "use client" modules so Server Components can import them too.

export const buttonClass =
  "w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white transition hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300";

export const inputClass =
  "mt-1 w-full rounded-md border border-zinc-300 bg-transparent px-3 py-2 focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900 focus:outline-none dark:border-zinc-700 dark:focus:border-zinc-100 dark:focus:ring-zinc-100";

export const secondaryButtonClass =
  "rounded-md border border-zinc-300 px-4 py-2 text-sm font-medium transition hover:bg-zinc-100 disabled:opacity-60 dark:border-zinc-700 dark:hover:bg-zinc-900";

export const dangerButtonClass =
  "rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-500 disabled:opacity-60";
