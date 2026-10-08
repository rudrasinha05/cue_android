// Delete only the authenticated caller's Cue data and Cue Auth identity.
function reply(status: number, message: string): Response {
  return new Response(JSON.stringify({ message }), {
    status, headers: { "content-type": "application/json", "cache-control": "no-store" },
  })
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return reply(405, "POST required")
  const token = /^Bearer (.+)$/.exec(request.headers.get("authorization") ?? "")?.[1]
  const url = Deno.env.get("SUPABASE_URL")
  const anon = Deno.env.get("SUPABASE_ANON_KEY")
  const admin = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")
  if (!token || !url || !anon || !admin) return reply(401, "Authentication unavailable")
  const input = await request.text().catch(() => "")
  if (input !== '{"confirm":"DELETE MY CUE ACCOUNT"}') return reply(400, "Confirmation required")

  const identity = await fetch(`${url}/auth/v1/user`, {
    headers: { authorization: `Bearer ${token}`, apikey: anon },
    signal: AbortSignal.timeout(5000),
  }).catch(() => null)
  if (!identity?.ok) return reply(401, "Sign in required")
  const user = await identity.json().catch(() => null)
  if (typeof user?.id !== "string" || !/^[0-9a-f-]{36}$/.test(user.id))
    return reply(401, "Sign in required")

  // The RPC derives auth.uid() from this user's JWT; no client-supplied owner ID.
  const data = await fetch(`${url}/rest/v1/rpc/delete_my_cue_data`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, apikey: anon,
      "content-type": "application/json" },
    body: "{}", signal: AbortSignal.timeout(8000),
  }).catch(() => null)
  if (!data?.ok) return reply(503, "Could not remove Cue data. Try again online")

  // The service key never leaves the function. The target ID comes only from /auth/v1/user.
  const deleted = await fetch(`${url}/auth/v1/admin/users/${user.id}`, {
    method: "DELETE",
    headers: { authorization: `Bearer ${admin}`, apikey: admin },
    signal: AbortSignal.timeout(8000),
  }).catch(() => null)
  if (!deleted?.ok) return reply(503, "Cue data removed, but account deletion needs a retry")
  return reply(200, "Cue account deleted")
})
