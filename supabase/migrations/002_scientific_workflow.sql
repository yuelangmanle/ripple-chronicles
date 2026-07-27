-- Keep cloud records compatible with the Android local JSON contract.
alter table datasets
    add column if not exists sampling_site text,
    add column if not exists latitude double precision,
    add column if not exists longitude double precision,
    add column if not exists sampled_at timestamp with time zone,
    add column if not exists water_depth_meters double precision,
    add column if not exists water_temperature_celsius double precision,
    add column if not exists ph double precision,
    add column if not exists salinity_psu double precision,
    add column if not exists sample_code text;

alter table plankton_images
    add column if not exists is_favorite boolean not null default false,
    add column if not exists identification_confidence smallint,
    add column if not exists review_status text not null default 'UNREVIEWED',
    add column if not exists review_note text,
    add column if not exists reviewed_at timestamp with time zone;

alter table plankton_images
    drop constraint if exists plankton_images_identification_confidence_check,
    add constraint plankton_images_identification_confidence_check
        check (identification_confidence between 0 and 100);

alter table plankton_images
    drop constraint if exists plankton_images_review_status_check,
    add constraint plankton_images_review_status_check
        check (review_status in ('UNREVIEWED', 'CONFIRMED', 'REJECTED'));

create index if not exists datasets_sample_code_idx on datasets(sample_code);
create index if not exists plankton_images_review_status_idx on plankton_images(review_status);
create index if not exists plankton_images_favorite_idx on plankton_images(is_favorite) where is_favorite;
