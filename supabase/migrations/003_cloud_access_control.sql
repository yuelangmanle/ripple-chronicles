-- Run this only after migrating existing cloud records to authenticated owners.
-- The mobile app remains local-first when no Supabase configuration is supplied.
alter table datasets add column if not exists owner_id uuid references auth.users(id);
alter table species add column if not exists owner_id uuid references auth.users(id);
alter table plankton_images add column if not exists owner_id uuid references auth.users(id);

alter table datasets enable row level security;
alter table species enable row level security;
alter table plankton_images enable row level security;

drop policy if exists "dataset owner access" on datasets;
create policy "dataset owner access" on datasets
    for all to authenticated
    using (auth.uid() = owner_id)
    with check (auth.uid() = owner_id);

drop policy if exists "species owner access" on species;
create policy "species owner access" on species
    for all to authenticated
    using (auth.uid() = owner_id or owner_id is null)
    with check (auth.uid() = owner_id);

drop policy if exists "image owner access" on plankton_images;
create policy "image owner access" on plankton_images
    for all to authenticated
    using (auth.uid() = owner_id)
    with check (auth.uid() = owner_id);
