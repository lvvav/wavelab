import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-admin-token",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (o: unknown, s = 200) =>
  new Response(JSON.stringify(o), { status: s, headers: { ...cors, "content-type": "application/json" } });

const enc = new TextEncoder();
const SECRET = Deno.env.get("ADMIN_SECRET") ?? "";
// если хоть один секрет не задан, функция никого не пускает
const CONFIGURED = ["ADMIN_USER", "ADMIN_PASS", "ADMIN_WORD", "ADMIN_SECRET"].every((n) => (Deno.env.get(n) ?? "").length > 0);
const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

async function hmac(msg: string) {
  const k = await crypto.subtle.importKey("raw", enc.encode(SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const s = await crypto.subtle.sign("HMAC", k, enc.encode(msg));
  return Array.from(new Uint8Array(s)).map((x) => x.toString(16).padStart(2, "0")).join("");
}
const eq = (a: string, b: string) => {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
};
const norm = (s: unknown) => String(s ?? "").trim().toLowerCase();

async function tokenOk(t: string | null) {
  if (!t) return false;
  const [exp, sig] = t.split(".");
  if (!exp || !sig || Number(exp) < Date.now()) return false;
  return eq(sig, await hmac("tok:" + exp));
}
const str = (v: unknown, n = 200) => String(v ?? "").slice(0, n);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!CONFIGURED) return json({ error: "not configured" }, 500);
  let b: any;
  try { b = await req.json(); } catch { return json({ error: "bad json" }, 400); }

  if (b.action === "login") {
    await new Promise((r) => setTimeout(r, 700)); // замедляем перебор
    const ok =
      eq(await hmac("u:" + norm(b.user)), await hmac("u:" + norm(Deno.env.get("ADMIN_USER")))) &&
      eq(await hmac("p:" + String(b.pass ?? "")), await hmac("p:" + String(Deno.env.get("ADMIN_PASS")))) &&
      eq(await hmac("w:" + norm(b.word)), await hmac("w:" + norm(Deno.env.get("ADMIN_WORD"))));
    if (!ok) return json({ error: "denied" }, 401);
    const exp = String(Date.now() + 365 * 24 * 3600 * 1000);
    return json({ token: exp + "." + (await hmac("tok:" + exp)) });
  }

  if (!(await tokenOk(req.headers.get("x-admin-token")))) return json({ error: "auth" }, 401);

  if (b.action === "save") {
    const items = (Array.isArray(b.items) ? b.items : []).slice(0, 300).map((x: any) => ({
      key: str(x.key, 400), data: { al: str(x.al), aa: str(x.aa) },
    })).filter((x: any) => x.key && x.data.al);
    if (items.length) {
      const { error } = await sb.from("album_edits").upsert(items.map((x: any) => ({ ...x, updated_at: new Date().toISOString() })));
      if (error) return json({ error: error.message }, 500);
    }
    return json({ ok: true, n: items.length });
  }

  if (b.action === "cover") {
    const m = /^data:image\/jpeg;base64,(.+)$/.exec(String(b.img ?? ""));
    if (!m || m[1].length > 2_000_000) return json({ error: "image" }, 400);
    const bytes = Uint8Array.from(atob(m[1]), (c) => c.charCodeAt(0));
    const name = (await hmac("cov:" + b.k)).slice(0, 24) + "-" + Date.now() + ".jpg";
    const up = await sb.storage.from("covers").upload(name, bytes, { contentType: "image/jpeg" });
    if (up.error) return json({ error: up.error.message }, 500);
    const url = sb.storage.from("covers").getPublicUrl(name).data.publicUrl;
    const { error } = await sb.from("album_edits").upsert({ key: "cov:" + str(b.k, 400), data: { img: url }, updated_at: new Date().toISOString() });
    if (error) return json({ error: error.message }, 500);
    return json({ ok: true, url });
  }

  if (b.action === "reset") {
    const keys = (Array.isArray(b.keys) ? b.keys : []).slice(0, 500).map((k: unknown) => str(k, 400));
    if (b.cov) keys.push("cov:" + str(b.cov, 400));
    if (keys.length) await sb.from("album_edits").delete().in("key", keys);
    return json({ ok: true });
  }

  return json({ error: "action" }, 400);
});
