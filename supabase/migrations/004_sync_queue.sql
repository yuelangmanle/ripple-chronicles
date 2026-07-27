-- Shared shape for the local offline queue and the optional cloud reconciliation log.
create table if not exists sync_operations (
    id uuid primary key default uuid_generate_v4(),
    owner_id uuid not null references auth.users(id) default auth.uid(),
    entity_type text not null,
    entity_id text not null,
    action text not null check (action in ('CREATE', 'UPDATE', 'DELETE')),
    payload jsonb not null default '{}'::jsonb,
    queued_at timestamp with time zone not null default timezone('utc'::text, now()),
    retry_count integer not null default 0 check (retry_count >= 0),
    conflict_state text not null default 'PENDING'
        check (conflict_state in ('PENDING', 'SYNCED', 'CONFLICT', 'FAILED'))
);

create index if not exists sync_operations_owner_state_idx
    on sync_operations(owner_id, conflict_state, queued_at);

alter table sync_operations enable row level security;
drop policy if exists "sync operation owner access" on sync_operations;
create policy "sync operation owner access" on sync_operations
    for all to authenticated
    using (auth.uid() = owner_id)
    with check (auth.uid() = owner_id);
