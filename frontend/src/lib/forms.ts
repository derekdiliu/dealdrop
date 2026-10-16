import "server-only";

/**
 * The submitted text fields, minus any password, for refilling a form after an error. Lives outside
 * the "use server" action files because every export there becomes a callable server action.
 */
export function submittedValues(form: FormData): Record<string, string> {
  const values: Record<string, string> = {};
  form.forEach((value, key) => {
    // Keys starting with $ are React's own action metadata.
    if (typeof value === "string" && key !== "password" && !key.startsWith("$")) {
      values[key] = value;
    }
  });
  return values;
}
