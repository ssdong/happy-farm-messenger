CREATE SCHEMA IF NOT EXISTS happyfarm;

SET search_path TO happyfarm;

CREATE TABLE IF NOT EXISTS happyfarm.users (
    user_canonical_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                text UNIQUE NOT NULL,
    password_hash       text NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_name_length_check CHECK (char_length(name) BETWEEN 1 AND 32)
);

CREATE TYPE happyfarm.friendship_status AS ENUM ('pending','accepted');

CREATE TABLE IF NOT EXISTS happyfarm.friendships (
    user_lo             uuid NOT NULL REFERENCES users(user_canonical_id) ON DELETE CASCADE,
    user_hi             uuid NOT NULL REFERENCES users(user_canonical_id) ON DELETE CASCADE,
    status              happyfarm.friendship_status NOT NULL,
    requester_id        uuid NOT NULL REFERENCES users(user_canonical_id),
    PRIMARY KEY         (user_lo, user_hi),
    CHECK               (user_lo < user_hi), -- enforces canonical ordering and no duplicate friendships i.e. no (a,b) and (b,a) at the same time
    CHECK               (requester_id = user_lo OR requester_id = user_hi)
);

CREATE INDEX IF NOT EXISTS friendships_by_user_lo ON friendships (user_lo);
CREATE INDEX IF NOT EXISTS friendships_by_user_hi ON friendships (user_hi);

CREATE TYPE happyfarm.room_type AS ENUM ('direct', 'group');

CREATE TABLE IF NOT EXISTS happyfarm.rooms (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type           happyfarm.room_type NOT NULL,
    title               text,                     -- groups use this; DMs ignore
    dm_fingerprint      text UNIQUE,              -- if both users try to start DM at the same one, it solves race condition. For multiple-member chat, this field is empty
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS happyfarm.room_members (
    room_id             uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_canonical_id   uuid NOT NULL REFERENCES users(user_canonical_id) ON DELETE CASCADE,
    joined_at           timestamptz NOT NULL DEFAULT now(),
    last_read_seq       bigint NOT NULL DEFAULT 0, -- for unread counts
    PRIMARY KEY         (room_id, user_canonical_id)
);

CREATE INDEX room_members_by_user ON room_members (user_canonical_id);

CREATE TYPE happyfarm.message_type AS ENUM ('text', 'image', 'audio');

CREATE TABLE IF NOT EXISTS happyfarm.image_blobs (
    image_id                uuid PRIMARY KEY,
    content_type            text NOT NULL,         -- e.g., image/webp, image/jpeg, image/png
    bytes                   bigint NOT NULL,       -- size in bytes
    sha256_hex              text NOT NULL,         -- integrity/dedup
    data                    bytea NOT NULL,        -- the encrypted image bytes
    encrypted_dek           bytea NOT NULL,
    nonce                   bytea NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (sha256_hex)
);

CREATE TABLE IF NOT EXISTS happyfarm.audio_blobs (
    audio_id                uuid PRIMARY KEY,
    content_type            text NOT NULL,         -- e.g., audio/ogg, audio/mpeg, audio/aac
    bytes                   bigint NOT NULL,       -- size in bytes
    sha256_hex              text NOT NULL,         -- integrity/dedup
    data                    bytea NOT NULL,        -- the encrypted audio bytes
    encrypted_dek           bytea NOT NULL,
    nonce                   bytea NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (sha256_hex)
);

CREATE TABLE IF NOT EXISTS happyfarm.messages (
    room_id                 uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    seq                     bigint NOT NULL,
    message_id              uuid NOT NULL,
    user_canonical_id       uuid REFERENCES users(user_canonical_id) ON DELETE SET NULL, -- don't cascade delete upon sender_id, if sender account is revoked, display "deleted user" in client

    message_type            happyfarm.message_type NOT NULL,
    message_text            bytea,
    encrypted_dek           bytea,
    nonce                   bytea,

    image_id                uuid REFERENCES image_blobs(image_id) ON DELETE SET NULL,

    audio_id                uuid REFERENCES audio_blobs(audio_id) ON DELETE SET NULL,

    created_at              timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY             (room_id, seq),
    UNIQUE                  (room_id, message_id), -- dedup upon client retries sending the same message in case of hiccups

    CONSTRAINT message_payload_consistency CHECK (
        (message_type = 'text'
        AND message_text IS NOT NULL
        AND encrypted_dek IS NOT NULL
        AND nonce IS NOT NULL
        AND image_id IS NULL
        AND audio_id IS NULL) OR
        (message_type = 'image'
        AND image_id IS NOT NULL
        AND message_text IS NULL
        AND encrypted_dek IS NULL
        AND nonce IS NULL) OR
        (message_type = 'audio'
        AND audio_id IS NOT NULL
        AND message_text IS NULL
        AND encrypted_dek IS NULL
        AND nonce IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS happyfarm.access_tokens (
    id                  bigserial PRIMARY KEY,
    user_canonical_id   uuid NOT NULL REFERENCES users(user_canonical_id) ON DELETE CASCADE,
    token_hash          text NOT NULL UNIQUE,
    expires_at          timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS happyfarm.registration_tokens (
    id                  bigserial PRIMARY KEY,
    token               text NOT NULL UNIQUE
);