// Supabase Edge Function: turn-creds
// Отдаёт сайту данные TURN-сервера. Всё секретное лежит только в секретах Supabase, не в репозитории.
// Любой TURN-сервер, два режима (секреты задаются командой `supabase secrets set ИМЯ=значение`):
//  1) свой coturn с use-auth-secret: TURN_URLS="turn:IP:3478,turn:IP:3478?transport=tcp,turns:IP:5349?transport=tcp" и TURN_SECRET=<static-auth-secret из turnserver.conf>
//     функция сама выдаёт временный логин/пароль на час
//  2) готовый сервис с постоянным логином (Metered, Xirsys и т.п.): TURN_URLS=... , TURN_USER=... , TURN_PASS=...
const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-headers': 'authorization, x-client-info, apikey, content-type',
  'content-type': 'application/json',
};

async function hmacB64(secret: string, msg: string) {
  const k = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
  const s = await crypto.subtle.sign('HMAC', k, new TextEncoder().encode(msg));
  return btoa(String.fromCharCode(...new Uint8Array(s)));
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS });
  const urls = (Deno.env.get('TURN_URLS') || '').split(',').map((x) => x.trim()).filter(Boolean);
  if (!urls.length) return new Response(JSON.stringify({ error: 'turn not configured' }), { status: 503, headers: CORS });
  const secret = Deno.env.get('TURN_SECRET');
  let username = Deno.env.get('TURN_USER') || '';
  let credential = Deno.env.get('TURN_PASS') || '';
  if (secret) {
    username = String(Math.floor(Date.now() / 1000) + 3600);
    credential = await hmacB64(secret, username);
  }
  if (!username || !credential) return new Response(JSON.stringify({ error: 'turn not configured' }), { status: 503, headers: CORS });
  return new Response(JSON.stringify({ iceServers: [{ urls, username, credential }] }), { headers: CORS });
});
