# Permissions Guide

MusicXCST separates physical disc ownership from stored audio ownership.

## Player Ownership

Each stored music entry records the creator's UUID and name in the server music index. Written Blueprint CDs also carry disc metadata on the `ItemStack`.

Normal players can:

- list their own entries
- inspect their own entries
- delete their own entries
- create discs through the CD Writer
- manage local cache downloads

Physical discs can still be traded, stored, dropped, or taken like normal items. Trading a disc does not transfer ownership of the server-side audio entry.

## Admin Access

Admins can inspect, delete, repair, reload, and test-play any music entry. Admin commands require the server's admin permission level.

Relevant config fields:

```text
allowFoundDiscsPlayback
ownerOnlyPlayback
adminBypass
allowAdminAbsoluteServerPaths
```

## Public Server Recommendations

- Keep `allowAdminAbsoluteServerPaths` disabled unless all admins are trusted with server filesystem access.
- Enable size, duration, and per-player file limits.
- Use `block_new_upload` or `confirm_delete_oldest` for stricter public moderation.
- Make server rules clear: players must only upload audio they have rights to use.
