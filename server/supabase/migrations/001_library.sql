-- Rios敖丙. Managed Auth supplies email registration, login, refresh and recovery.
create table public.library_records (
 user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
 kind text not null check(kind in ('favorite','history')),
 content_key text not null check(length(content_key) between 1 and 512),
 payload jsonb not null default '{}' check(jsonb_typeof(payload)='object' and octet_length(payload::text)<=16384),
 deleted boolean not null default false,
 updated_at timestamptz not null default now(),
 primary key(user_id,kind,content_key)
);
alter table public.library_records enable row level security;
create policy own_library on public.library_records for all to authenticated
 using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
revoke all on public.library_records from anon, authenticated;
grant select on public.library_records to authenticated;
grant insert(user_id,kind,content_key,payload,deleted) on public.library_records to authenticated;
grant update(user_id,kind,content_key,payload,deleted) on public.library_records to authenticated;
create function public.library_touch() returns trigger language plpgsql set search_path='' as $$
begin new.updated_at=clock_timestamp(); return new; end $$;
create trigger library_touch before update on public.library_records for each row execute function public.library_touch();
create index library_by_user_time on public.library_records(user_id,updated_at);
