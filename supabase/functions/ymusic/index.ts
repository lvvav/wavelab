// Supabase Edge Function: ymusic
// Личный режим: поиск в Яндекс Музыке и ссылка на ПОЛНЫЙ трек (нужна подписка Плюс на аккаунте токена).
// Секреты (Supabase → Edge Functions → Secrets), в репозитории их нет:
//   YANDEX_TOKEN     OAuth-токен аккаунта Яндекса с Плюсом
//   YM_OWNER_EMAIL   e-mail, с которым ты входишь на сайт; только этому пользователю функция отвечает
// Действия (POST, json): {a:'status'} | {a:'search', q} | {a:'album', id} | {a:'artist', id} | {a:'url', id}
import { createHash } from "node:crypto";

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, x-client-info, apikey, content-type",
  "access-control-allow-methods": "POST, OPTIONS",
  "content-type": "application/json",
};
const json = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });

const API = "https://api.music.yandex.net";
const TOKEN = Deno.env.get("YANDEX_TOKEN") ?? "";
const OWNER = (Deno.env.get("YM_OWNER_EMAIL") ?? "").trim().toLowerCase();
const HEAD = {
  Authorization: "OAuth " + TOKEN,
  "X-Yandex-Music-Client": "YandexMusicAndroid/24023231",
  "User-Agent": "Yandex-Music-API",
  "Accept-Language": "ru",
};

// проверяем, что запрос пришёл от владельца: настоящий вход на сайт + совпадение e-mail
async function isOwner(req: Request): Promise<boolean> {
  if (!OWNER) return false;
  const auth = req.headers.get("authorization") ?? "";
  if (!/^Bearer eyJ/.test(auth)) return false; // анонимный ключ сайта не годится, нужен вход в аккаунт
  try {
    const r = await fetch(Deno.env.get("SUPABASE_URL")! + "/auth/v1/user", {
      headers: { apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", authorization: auth },
    });
    if (!r.ok) return false;
    const u = await r.json();
    return String(u?.email ?? "").trim().toLowerCase() === OWNER;
  } catch {
    return false;
  }
}

async function ym(path: string) {
  const r = await fetch(API + path, { headers: HEAD });
  const txt = await r.text();
  let j: any = null;
  try { j = JSON.parse(txt); } catch { /* не json */ }
  return { ok: r.ok, status: r.status, j, txt };
}

const cover = (uri?: string) => (uri ? "https://" + uri.replace("%%", "400x400") : "");

function mapTrack(t: any) {
  return {
    id: String(t.id),
    t: t.title + (t.version ? " (" + t.version + ")" : ""),
    a: (t.artists ?? []).map((x: any) => x.name).join(", "),
    al: t.albums?.[0]?.title ?? "",
    dur: Math.round((t.durationMs ?? 0) / 1000),
    img: cover(t.coverUri ?? t.albums?.[0]?.coverUri),
    ok: t.available !== false,
  };
}

const names = (a: any[] | undefined) => (a ?? []).map((x: any) => x.name).join(", ");
function mapAlbum(a: any) {
  return {
    id: String(a.id),
    t: a.title + (a.version ? " (" + a.version + ")" : ""),
    a: names(a.artists),
    y: a.year ?? a.releaseDate?.slice(0, 4) ?? "",
    n: a.trackCount ?? 0,
    aid: String(a.artists?.[0]?.id ?? ""),
    img: cover(a.coverUri ?? a.ogImage),
  };
}
function mapArtist(x: any) {
  return { id: String(x.id), t: x.name, img: cover(x.cover?.uri ?? x.ogImage) };
}

// способ 1 (классический): download-info → xml → подпись md5 → ссылка get-mp3
async function urlClassic(id: string) {
  const di = await ym(`/tracks/${id}/download-info`);
  const list: any[] = di.j?.result ?? [];
  if (!list.length) throw new Error("download-info пуст (" + di.status + ")");
  const full = list.filter((x) => !x.preview && x.codec === "mp3").sort((a, b) => b.bitrateInKbps - a.bitrateInKbps);
  if (!full.length) throw new Error("preview-only");
  const pick = full[0];
  const r = await fetch(pick.downloadInfoUrl + (pick.downloadInfoUrl.includes("?") ? "&" : "?") + "format=json", { headers: HEAD });
  const body = await r.text();
  let host = "", path = "", ts = "", s = "";
  try {
    const j = JSON.parse(body);
    host = j.host; path = j.path; ts = j.ts; s = j.s;
  } catch {
    const g = (n: string) => (body.match(new RegExp("<" + n + ">([^<]*)</" + n + ">")) ?? [])[1] ?? "";
    host = g("host"); path = g("path"); ts = g("ts"); s = g("s");
  }
  if (!host || !path || !ts || !s) throw new Error("разбор download-info не удался");
  const sign = createHash("md5").update("XGRlBW9FXlekgbPrRHuSiA" + path.slice(1) + s).digest("hex");
  return { url: `https://${host}/get-mp3/${sign}/${ts}${path}`, codec: "mp3", bitrate: pick.bitrateInKbps, flow: "classic" };
}

// способ 2 (запасной): get-file-info с подписью, без шифрования (transports=raw)
async function urlNew(id: string) {
  const ts = Math.floor(Date.now() / 1000);
  const quality = "nq", codecs = "mp3,aac,he-aac", transports = "raw";
  const msg = `${ts}${id}${quality}${codecs.replace(/,/g, "")}${transports}`;
  const k = await crypto.subtle.importKey("raw", new TextEncoder().encode("kzqU4XhfCaY6B6JTHODeq5"), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(msg))))).replace(/=+$/, "");
  const q = new URLSearchParams({ ts: String(ts), trackId: id, quality, codecs, transports, sign: sig });
  const r = await ym("/get-file-info?" + q.toString());
  const d = r.j?.result?.downloadInfo;
  if (!d?.url) throw new Error("get-file-info без ссылки (" + r.status + ")");
  if (d.key) throw new Error("ссылка зашифрована, этот способ не подходит");
  if (d.preview || d.trackPreview) throw new Error("preview-only");
  return { url: String(d.url), codec: String(d.codec ?? ""), bitrate: d.bitrate ?? 0, flow: "new" };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!TOKEN) return json({ error: "no_token", detail: "Не задан секрет YANDEX_TOKEN" }, 503);
  if (!OWNER) return json({ error: "no_owner", detail: "Не задан секрет YM_OWNER_EMAIL" }, 503);
  if (!(await isOwner(req))) return json({ error: "denied", detail: "Войдите на сайт своим аккаунтом" }, 403);

  let b: any = {};
  try { b = await req.json(); } catch { /* пусто */ }
  try {
    if (b.a === "status") {
      const r = await ym("/account/status");
      const res = r.j?.result;
      if (!r.ok || !res) return json({ error: "token", detail: "Яндекс не принял токен (" + r.status + ")" }, 502);
      return json({ login: res.account?.login ?? "", plus: !!res.plus?.hasPlus });
    }
    if (b.a === "search") {
      const q = String(b.q ?? "").trim().slice(0, 120);
      if (!q) return json({ tracks: [] });
      const r = await ym("/search?" + new URLSearchParams({ text: q, nocorrect: "false", type: "all", page: "0", "playlist-in-best": "true" }));
      if (!r.ok) return json({ error: "search", detail: "Поиск не удался (" + r.status + ")" }, 502);
      const res = r.j?.result ?? {};
      return json({
        tracks: (res.tracks?.results ?? []).map(mapTrack).filter((x: any) => x.ok).slice(0, 15),
        albums: (res.albums?.results ?? []).map(mapAlbum).slice(0, 8),
        artists: (res.artists?.results ?? []).map(mapArtist).slice(0, 6),
      });
    }
    if (b.a === "album") {
      const id = String(b.id ?? "").replace(/\D/g, "");
      if (!id) return json({ error: "id" }, 400);
      const r = await ym(`/albums/${id}/with-tracks`);
      const al = r.j?.result;
      if (!r.ok || !al) return json({ error: "album", detail: "Альбом не открылся (" + r.status + ")" }, 502);
      const img = cover(al.coverUri);
      const discs = (al.volumes ?? [])
        .map((v: any[]) => v.map(mapTrack).filter((x: any) => x.ok).map((x: any) => ({ ...x, img: x.img || img })))
        .filter((d: any[]) => d.length);
      return json({ album: mapAlbum(al), discs });
    }
    if (b.a === "artist") {
      const id = String(b.id ?? "").replace(/\D/g, "");
      if (!id) return json({ error: "id" }, 400);
      const [br, da] = await Promise.all([
        ym(`/artists/${id}/brief-info`),
        ym(`/artists/${id}/direct-albums?page=0&page-size=40&sort-by=year`),
      ]);
      const info = br.j?.result;
      if (!br.ok || !info) return json({ error: "artist", detail: "Артист не открылся (" + br.status + ")" }, 502);
      const albums = da.j?.result?.albums ?? info.albums ?? [];
      return json({
        artist: { id, t: info.artist?.name ?? "", img: cover(info.artist?.cover?.uri ?? info.artist?.ogImage) },
        tracks: (info.popularTracks ?? []).map(mapTrack).filter((x: any) => x.ok).slice(0, 15),
        albums: albums.map(mapAlbum).slice(0, 40),
      });
    }
    if (b.a === "url") {
      const id = String(b.id ?? "").replace(/[^0-9:]/g, "");
      if (!id) return json({ error: "id" }, 400);
      const errs: string[] = [];
      for (const f of [urlClassic, urlNew]) {
        try { return json(await f(id)); } catch (e) { errs.push(String((e as Error).message)); }
      }
      const prev = errs.some((e) => e === "preview-only");
      return json({ error: prev ? "preview" : "url", detail: errs.join(" | ") }, 502);
    }
    return json({ error: "action" }, 400);
  } catch (e) {
    return json({ error: "fail", detail: String((e as Error).message) }, 500);
  }
});
