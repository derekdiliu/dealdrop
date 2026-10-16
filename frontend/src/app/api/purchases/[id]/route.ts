import { forwardWithSession } from "@/lib/proxy";

export async function GET(_request: Request, { params }: RouteContext<"/api/purchases/[id]">) {
  const { id } = await params;
  return forwardWithSession(`/api/flash-sale/purchases/${encodeURIComponent(id)}`, "GET");
}
