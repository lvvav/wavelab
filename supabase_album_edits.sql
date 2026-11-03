-- Выполнить один раз: Supabase → SQL Editor
create table if not exists public.album_edits (
  key text primary key,            -- ключ трека, либо 'cov:' + ключ альбома
  data jsonb not null,             -- {al, aa} для трека или {img} для обложки
  updated_at timestamptz default now()
);
alter table public.album_edits enable row level security;
drop policy if exists "read all" on public.album_edits;
create policy "read all" on public.album_edits for select using (true);
-- политик на запись нет: писать может только Edge Function (service role)

insert into storage.buckets (id, name, public) values ('covers','covers',true)
on conflict (id) do nothing;
drop policy if exists "covers read" on storage.objects;
create policy "covers read" on storage.objects for select using (bucket_id = 'covers');
