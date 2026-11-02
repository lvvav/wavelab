-- Совместные плейлисты SoundLife
-- Запустить один раз: Supabase -> SQL Editor -> New query -> вставить -> Run

create table if not exists public.collab_playlists (
  id uuid primary key,
  owner uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 80),
  created_at timestamptz not null default now()
);

create table if not exists public.collab_members (
  playlist_id uuid not null references public.collab_playlists(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  added_at timestamptz not null default now(),
  primary key (playlist_id, user_id)
);
create index if not exists collab_members_user_idx on public.collab_members (user_id);

create table if not exists public.collab_tracks (
  playlist_id uuid not null references public.collab_playlists(id) on delete cascade,
  track_key text not null check (char_length(track_key) between 1 and 300),
  added_by uuid references auth.users(id) on delete set null default auth.uid(),
  added_at timestamptz not null default now(),
  primary key (playlist_id, track_key)
);

-- вспомогательные проверки (без бесконечной рекурсии в правилах)
create or replace function public.collab_can(pid uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.collab_playlists p
    where p.id = pid and (
      p.owner = auth.uid()
      or exists (select 1 from public.collab_members m where m.playlist_id = p.id and m.user_id = auth.uid())
    )
  );
$$;

create or replace function public.collab_owns(pid uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.collab_playlists p where p.id = pid and p.owner = auth.uid());
$$;

revoke all on function public.collab_can(uuid) from public;
revoke all on function public.collab_owns(uuid) from public;
grant execute on function public.collab_can(uuid) to authenticated;
grant execute on function public.collab_owns(uuid) to authenticated;

alter table public.collab_playlists enable row level security;
alter table public.collab_members enable row level security;
alter table public.collab_tracks enable row level security;

-- плейлисты
drop policy if exists cp_select on public.collab_playlists;
create policy cp_select on public.collab_playlists
  for select to authenticated using (owner = auth.uid() or public.collab_can(id));
drop policy if exists cp_insert on public.collab_playlists;
create policy cp_insert on public.collab_playlists
  for insert to authenticated with check (owner = auth.uid());
drop policy if exists cp_update on public.collab_playlists;
create policy cp_update on public.collab_playlists
  for update to authenticated using (owner = auth.uid()) with check (owner = auth.uid());
drop policy if exists cp_delete on public.collab_playlists;
create policy cp_delete on public.collab_playlists
  for delete to authenticated using (owner = auth.uid());

-- участники
drop policy if exists cm_select on public.collab_members;
create policy cm_select on public.collab_members
  for select to authenticated using (public.collab_can(playlist_id));
drop policy if exists cm_insert on public.collab_members;
create policy cm_insert on public.collab_members
  for insert to authenticated with check (public.collab_owns(playlist_id));
drop policy if exists cm_delete on public.collab_members;
create policy cm_delete on public.collab_members
  for delete to authenticated using (public.collab_owns(playlist_id) or user_id = auth.uid());

-- треки
drop policy if exists ct_select on public.collab_tracks;
create policy ct_select on public.collab_tracks
  for select to authenticated using (public.collab_can(playlist_id));
drop policy if exists ct_insert on public.collab_tracks;
create policy ct_insert on public.collab_tracks
  for insert to authenticated with check (public.collab_can(playlist_id) and added_by = auth.uid());
drop policy if exists ct_delete on public.collab_tracks;
create policy ct_delete on public.collab_tracks
  for delete to authenticated using (public.collab_can(playlist_id));

revoke all on public.collab_playlists, public.collab_members, public.collab_tracks from anon;
grant select, insert, update, delete on public.collab_playlists, public.collab_members, public.collab_tracks to authenticated;
