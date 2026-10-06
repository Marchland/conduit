# Project agent memory

Conduit is the distribution service for Jacob's site: it **syndicates** posts to
downstream micropub targets (such as Bridgy) and **publishes WebSub**. It owns
syndication state; it does **not** own content (that is Bastion).

## Repository layout

Multi-module, mirroring Sigil/Beacon:

- `conduit-client/` - published library (`dev.jacobandersen:conduit-client`): the
  producer-owned event schemas (`dev.jacobandersen.conduit.event.SyndicationEvent`,
  `ConduitSubjects`, stream `DISTRIBUTION`, subjects `syndication.syndicated|retracted`,
  `websub.>`), and the control-API types (`dev.jacobandersen.conduit.api`).
- `conduit-app/` - the Spring Boot server (`bootJar` -> `conduit.jar`).

## How it works

- **Event-driven**: consumes Bastion's `content.post.*` from the JetStream
  `CONTENT` stream with a durable consumer (`conduit.events.nats.*`), guarded by
  a per-post `version` high-water mark (`content_event_checkpoint`).
- **Syndication**: `SyndicationReconciliationService` reconciles the post's
  desired targets (the event's `syndicationTargets`) against the copies Conduit
  has recorded (`post_syndications`): public posts get a copy (or are re-based on
  a rename); targets no longer desired are retracted; non-public/deleted posts
  retract copies but retain desired targets so a re-publish re-syndicates. Emits
  `syndication.syndicated|retracted`. The downstream copy is an excerpt +
  permalink (`SyndicationContentMapper`, grapheme-bounded).
- **WebSub**: on any content change, pings configured hubs
  (`conduit.websub.hubs` + `topic-url`); near-stateless.
- **No cross-service FK**: posts are referenced by the content service's post id.

## Build and test

- `./gradlew test` runs the suite (Testcontainers Postgres + NATS for the
  integration test); `./gradlew ktlintCheck` lints (`ktlintFormat` fixes).
- Config: `conduit.syndication.*` (targets, budgets), `conduit.websub.*`,
  `conduit.events.nats.*`. See `conduit-app/src/main/resources/application.yaml`.
- Jackson 3 (`tools.jackson.*`), not Jackson 2.
- Streams are **per-producer**: Conduit owns `SYNDICATION` (`syndication.>`,
  plus `websub.>` when emitted); Beacon owns `WEBMENTION`. Never share a stream
  between producers.
