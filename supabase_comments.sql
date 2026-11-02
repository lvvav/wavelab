-- Комментарии к трекам SoundLife
-- Запустить один раз: Supabase -> SQL Editor -> New query -> вставить -> Run

create table if not exists public.track_comments (
  id bigint generated always as identity primary key,
  track_key text not null check (char_length(track_key) between 1 and 300),
  user_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 500),
  created_at timestamptz not null default now()
);

create index if not exists track_comments_key_idx
  on public.track_comments (track_key, created_at desc);

alter table public.track_comments enable row level security;

drop policy if exists "comments read" on public.track_comments;
create policy "comments read" on public.track_comments
  for select using (true);

drop policy if exists "comments insert own" on public.track_comments;
create policy "comments insert own" on public.track_comments
  for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "comments delete own" on public.track_comments;
create policy "comments delete own" on public.track_comments
  for delete to authenticated using (auth.uid() = user_id);

grant select on public.track_comments to anon, authenticated;
grant insert, delete on public.track_comments to authenticated;
