/// <reference path="../deno.d.ts" />


// Supabase Edge Functions use Deno. 
// Deno.serve is the modern, built-in way to handle requests without external imports.

Deno.serve(async (req: Request) => {
  try {
    // 1. Handle JSON body
    const body = await req.json()
    const code = body.code
    const verifier = body.verifier

    // 2. Get environment variables (ensure these are set in Supabase)
    const clientId = "a5949efaa0b54f29b37220ad1c3eda18"
    const clientSecret = "b8b6df65d31c497ebc83b6aa7e308a83"

    // 3. Prepare Basic Auth header
    const basic = btoa(`${clientId}:${clientSecret}`)

    // 4. Exchange code for tokens
    const spotifyResponse = await fetch("https://accounts.spotify.com/api/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Authorization": `Basic ${basic}`
      },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code: code,
        redirect_uri: "vibevault://spotify-auth-callback",
        code_verifier: verifier
      })
    })

    const data = await spotifyResponse.json()

    // 5. Return Spotify's response to the app
    return new Response(
      JSON.stringify(data),
      {
        status: spotifyResponse.status,
        headers: { "Content-Type": "application/json" }
      }
    )

  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    return new Response(
      JSON.stringify({ error: errorMessage }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
  }
})
