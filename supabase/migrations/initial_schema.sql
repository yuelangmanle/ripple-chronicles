
-- Enable necessary extensions
create extension if not exists "uuid-ossp";

-- Datasets table
create table if not exists datasets (
    id uuid default uuid_generate_v4() primary key,
    name text not null,
    description text,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Species taxonomy table
create table if not exists species (
    id uuid default uuid_generate_v4() primary key,
    name_cn text,
    name_latin text,
    category text, -- 浮游动物, 浮游植物, etc.
    class_name text,
    order_name text,
    family_name text,
    genus_name text,
    source text default 'built-in', -- built-in, user-imported
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Plankton images table
create table if not exists plankton_images (
    id uuid default uuid_generate_v4() primary key,
    dataset_id uuid references datasets(id) on delete cascade,
    species_id uuid references species(id) on delete set null,
    image_url text not null,
    custom_name text,
    metadata jsonb default '{}'::jsonb,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Storage bucket for images
-- Note: Policies need to be set manually or via SQL if supported
-- insert into storage.buckets (id, name, public) values ('plankton-images', 'plankton-images', true);
