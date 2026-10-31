-- SoundLife: личные сообщения между пользователями.
-- Вставьте в Supabase -> SQL Editor -> Run. Можно запускать повторно.

create table if not exists public.messages (
  id         bigint generated always as identity primary key,
  sender     uuid not null default auth.uid() references auth.users(id) on delete cascade,
  recipient  uuid not null references auth.users(id) on delete cascade,
  body       text not null default '' check (char_length(body) <= 1000),
  track      text,
  created_at timestamptz not null default now(),
  read_at    timestamptz,
  check (sender <> recipient),
  check (char_length(body) > 0 or track is not null)
);

create index if not exists messages_recipient_idx on public.messages (recipient, created_at desc);
create index if not exists messages_sender_idx    on public.messages (sender, created_at desc);

alter table public.messages enable row level security;

drop policy if exists msg_select on public.messages;
drop policy if exists msg_insert on public.messages;
drop policy if exists msg_update on public.messages;
drop policy if exists msg_delete on public.messages;
create policy msg_select on public.messages for select to authenticated using (auth.uid() in (sender, recipient));
create policy msg_insert on public.messages for insert to authenticated with check (sender = auth.uid());
create policy msg_update on public.messages for update to authenticated using (recipient = auth.uid()) with check (recipient = auth.uid());
create policy msg_delete on public.messages for delete to authenticated using (sender = auth.uid());

-- получатель может менять только отметку «прочитано»
revoke update on public.messages from authenticated;
grant update (read_at) on public.messages to authenticated;

-- мгновенная доставка
do $$ begin
  alter publication supabase_realtime add table public.messages;
exception when duplicate_object then null;
end $$;
