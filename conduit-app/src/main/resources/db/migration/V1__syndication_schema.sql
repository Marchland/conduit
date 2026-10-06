-- Conduit's schema: syndication state. Posts are referenced by the content
-- service's post id (no cross-service FK). WebSub is near-stateless and needs
-- no tables.

create table post_syndications (
    id uuid primary key,
    post_id uuid not null,
    target_uid text not null,
    syndicated_url text,
    created_at_utc timestamptz not null default now()
);

create unique index uq_post_syndications_post_target on post_syndications (post_id, target_uid);
create index idx_post_syndications_post_id on post_syndications (post_id);

-- Per-post high-water mark for consumed content.post.* events (version guard).
create table content_event_checkpoint (
    post_id uuid primary key,
    version bigint not null,
    updated_at_utc timestamptz not null default now()
);
