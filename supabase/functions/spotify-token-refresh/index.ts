/// <reference path="../deno.d.ts" />


// Supabase Edge Functions use Deno.
// Deno.serve is the modern, built-in way to handle requests.

Deno.serve(async (req: Request) => {
  try {
    const { refresh_token } = await req.json()

    if (!refresh_token) {
      return new Response(
        JSON.stringify({ error: "Missing refresh_token" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }

    const clientId = "a5949efaa0b54f29b37220ad1c3eda18"
    const clientSecret = "690056aea533424d85e6fe27486c1694"

    // Prepare Basic Auth header (Preferred by Spotify)
    const basic = btoa(`${clientId}:${clientSecret}`)

    const response = await fetch("https://accounts.spotify.com/api/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Authorization": `Basic ${basic}`
      },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: refresh_token,
      })
    })

    const data = await response.json()

    return new Response(JSON.stringify(data), {
      status: response.status,
      headers: { "Content-Type": "application/json" },
    })
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    return new Response(
      JSON.stringify({ error: errorMessage }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
  }
})
