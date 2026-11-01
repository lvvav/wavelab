// Supabase Edge Function: turn-creds
// Отдаёт сайту временные данные TURN-сервера (Cloudflare Calls TURN). Ключи лежат только в секретах Supabase, не в репозитории:
//   supabase secrets set CF_TURN_KEY_ID=... CF_TURN_API_TOKEN=...
// Развернуть: supabase functions deploy turn-creds
const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-headers': 'authorization, x-client-info, apikey, content-type',
  'content-type': 'application/json',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS });
  const id = Deno.env.get('CF_TURN_KEY_ID');
  const tok = Deno.env.get('CF_TURN_API_TOKEN');
  if (!id || !tok) return new Response(JSON.stringify({ error: 'turn not configured' }), { status: 503, headers: CORS });
  const r = await fetch(`https://rtc.live.cloudflare.com/v1/turn/keys/${id}/credentials/generate-ice-servers`, {
    method: 'POST',
    headers: { authorization: `Bearer ${tok}`, 'content-type': 'application/json' },
    body: JSON.stringify({ ttl: 3600 }),
  });
  if (!r.ok) return new Response(JSON.stringify({ error: 'turn upstream ' + r.status }), { status: 502, headers: CORS });
  const j = await r.json();
  return new Response(JSON.stringify({ iceServers: j.iceServers || [] }), { headers: CORS });
});
