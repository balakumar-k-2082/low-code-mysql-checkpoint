# MySQL Checkpoint System - Architecture Flowcharts

## 1. Overall System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Application Layer                        │
│  ┌──────────────────────┐          ┌─────────────────────────┐  │
│  │  CheckpointManager   │          │      CLI Tool           │  │
│  │  - createCheckpoint()│          │  - Interactive Demo     │  │
│  │  - undo()           │          │  - Form Field Example   │  │
│  │  - redo()           │          │                         │  │
│  │  - revertTo()       │          │                         │  │
│  └──────────────────────┘          └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Library Core Layer                          │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │ SnapshotCapture │  │ CheckpointHistory│  │ StateRestorer  │ │
│  │ - captureTable()│  │ - trackPosition()│  │ - restoreData()│ │
│  │ - captureAllDB()│  │ - getNext()      │  │ - clearTable() │ │
│  └─────────────────┘  └──────────────────┘  └────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MySQL Database Layer                          │
│                                                                   │
│  ┌──────────────────────┐       ┌──────────────────────────┐   │
│  │  Application Tables  │       │   Checkpoint Tables      │   │
│  │  ─────────────────── │       │   ─────────────────────  │   │
│  │  • form_fields       │       │   • checkpoint_metadata  │   │
│  │  • users             │       │   • checkpoint_snapshots │   │
│  │  • orders            │       │   • user_checkpoint_pos  │   │
│  │  • ... (any table)   │       │                          │   │
│  └──────────────────────┘       └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Checkpoint Creation Flow

```
                        User calls: createCheckpoint("checkpoint_1")
                                         │
                                         ▼
                    ┌────────────────────────────────────────┐
                    │  1. Start Database Transaction         │
                    │     BEGIN;                             │
                    └────────────────────────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────────┐
                    │  2. Create Checkpoint Metadata         │
                    │  ─────────────────────────────────     │
                    │  INSERT INTO checkpoint_metadata       │
                    │  VALUES (                              │
                    │    id: 101,                            │
                    │    name: "checkpoint_1",               │
                    │    user_id: "user123",                 │
                    │    created_at: NOW(),                  │
                    │    parent_id: 100  -- previous CP      │
                    │  )                                     │
                    └────────────────────────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────────┐
                    │  3. Query All Application Tables       │
                    │  ─────────────────────────────────     │
                    │  SELECT table_name                     │
                    │  FROM information_schema.tables        │
                    │  WHERE table_schema = 'mydb'           │
                    │    AND table_name NOT LIKE 'checkpoint%'│
                    └────────────────────────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────────┐
                    │  4. FOR EACH Table in Database         │
                    │     Loop through: form_fields, users...│
                    └────────────────────────────────────────┘
                                         │
                    ┌────────────────────┴─────────────────────┐
                    │                                           │
                    ▼                                           ▼
        ┌─────────────────────────┐           ┌──────────────────────────┐
        │ 5a. JSON Storage        │           │ 5b. Key-Value Storage    │
        │ (for form_fields)       │           │ (for users, orders, etc) │
        │ ─────────────────────   │           │ ───────────────────────  │
        │ SELECT * FROM           │           │ SELECT * FROM users;     │
        │   form_fields;          │           │                          │
        │                         │           │ FOR EACH row:            │
        │ Convert to JSON Array:  │           │   INSERT INTO            │
        │ [                       │           │   checkpoint_snapshots   │
        │   {id:1, field_name:..},│           │   VALUES (               │
        │   {id:2, field_name:..} │           │     checkpoint_id: 101,  │
        │ ]                       │           │     table_name: "users", │
        │                         │           │     row_id: "user_1",    │
        │ INSERT INTO             │           │     row_data: JSON(row)  │
        │ checkpoint_snapshots    │           │   )                      │
        │ VALUES (                │           │                          │
        │   checkpoint_id: 101,   │           └──────────────────────────┘
        │   table_name:           │
        │     "form_fields",      │
        │   snapshot_data:        │
        │     [JSON_ARRAY]        │
        │ )                       │
        └─────────────────────────┘
                    │                                           │
                    └────────────────────┬─────────────────────┘
                                         ▼
                    ┌────────────────────────────────────────┐
                    │  6. Update User's Current Position     │
                    │  ─────────────────────────────────     │
                    │  UPDATE user_checkpoint_position       │
                    │  SET current_checkpoint_id = 101       │
                    │  WHERE user_id = "user123"             │
                    └────────────────────────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────────┐
                    │  7. Commit Transaction                 │
                    │     COMMIT;                            │
                    └────────────────────────────────────────┘
                                         │
                                         ▼
                                    ✅ Success
                            Checkpoint "checkpoint_1" created!
```

---

## 3. Undo Operation Flow

```
                        User calls: undo()
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  1. Get User's Current Position        │
        │  ─────────────────────────────────     │
        │  SELECT current_checkpoint_id          │
        │  FROM user_checkpoint_position         │
        │  WHERE user_id = "user123"             │
        │  → Result: checkpoint_id = 103         │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  2. Find Previous Checkpoint           │
        │  ─────────────────────────────────     │
        │  SELECT parent_checkpoint_id           │
        │  FROM checkpoint_metadata              │
        │  WHERE id = 103                        │
        │  → Result: parent_id = 102             │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  3. Begin Restore Transaction          │
        │     BEGIN;                             │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  4. Get All Tables in Checkpoint 102   │
        │  ─────────────────────────────────     │
        │  SELECT DISTINCT table_name            │
        │  FROM checkpoint_snapshots             │
        │  WHERE checkpoint_id = 102             │
        │  → [form_fields, users, orders]        │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  5. FOR EACH Table                     │
        └────────────────────────────────────────┘
                             │
        ┌────────────────────┴────────────────┐
        │                                      │
        ▼                                      ▼
┌────────────────────┐          ┌──────────────────────────┐
│ 5a. Clear Table    │          │ 5b. Restore Snapshot     │
│                    │          │                          │
│ DELETE FROM        │          │ For JSON storage:        │
│   form_fields;     │    ─OR─  │   INSERT INTO form_fields│
│                    │          │   SELECT * FROM JSON...  │
│ DELETE FROM users; │          │                          │
│                    │          │ For KV storage:          │
│                    │          │   INSERT INTO users      │
│                    │          │   SELECT row_data        │
│                    │          │   FROM checkpoint_snap.. │
│                    │          │   WHERE checkpoint_id=102│
└────────────────────┘          └──────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  6. Update User Position to 102        │
        │  ─────────────────────────────────     │
        │  UPDATE user_checkpoint_position       │
        │  SET current_checkpoint_id = 102       │
        │  WHERE user_id = "user123"             │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  7. Commit Transaction                 │
        │     COMMIT;                            │
        └────────────────────────────────────────┘
                             │
                             ▼
                        ✅ Success
            Database restored to checkpoint 102!
```

---

## 4. Redo Operation Flow

```
                        User calls: redo()
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  1. Get User's Current Position        │
        │     current_checkpoint_id = 102        │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  2. Find Next Checkpoint               │
        │  ─────────────────────────────────     │
        │  SELECT id FROM checkpoint_metadata    │
        │  WHERE parent_checkpoint_id = 102      │
        │    AND user_id = "user123"             │
        │  ORDER BY created_at ASC LIMIT 1       │
        │  → Result: next_id = 103               │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  3. Restore to Checkpoint 103          │
        │     (Same process as UNDO step 3-7)    │
        │     - Begin transaction                │
        │     - Clear all tables                 │
        │     - Restore from checkpoint_id=103   │
        │     - Update position to 103           │
        │     - Commit                           │
        └────────────────────────────────────────┘
                             │
                             ▼
                        ✅ Success
            Database restored to checkpoint 103!
```

---

## 5. Revert to Specific Checkpoint

```
                User calls: revertTo("checkpoint_name")
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  1. Find Checkpoint by Name            │
        │  ─────────────────────────────────     │
        │  SELECT id FROM checkpoint_metadata    │
        │  WHERE name = "checkpoint_name"        │
        │    AND user_id = "user123"             │
        │  → Result: checkpoint_id = 105         │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  2. Restore to Checkpoint 105          │
        │     (Same process as UNDO)             │
        └────────────────────────────────────────┘
                             │
                             ▼
                        ✅ Success
```

---

## 6. Data Structure Visualization

```
CHECKPOINT TIMELINE FOR USER "user123"
─────────────────────────────────────────────────────────────

Time: ──────────────────────────────────────────────────────►

         ┌──────┐      ┌──────┐      ┌──────┐      ┌──────┐
         │ CP1  │─────►│ CP2  │─────►│ CP3  │─────►│ CP4  │
         │ id:1 │      │ id:2 │      │ id:3 │      │ id:4 │
         └──────┘      └──────┘      └──────┘      └──────┘
         parent:NULL   parent:1      parent:2      parent:3
                                          ▲
                                          │
                                    Current Position
                                    (user123 is here)

Each checkpoint contains FULL DATABASE SNAPSHOT:
┌────────────────────────────────────────────────────────┐
│ Checkpoint 3 (CP3)                                     │
│ ────────────────────────────────────────────────────   │
│                                                        │
│  form_fields table (JSON):                            │
│  [{id:1, props:{color:"red"}},                        │
│   {id:2, props:{color:"blue"}}]                       │
│                                                        │
│  users table (Key-Value):                             │
│  Row 1: {id:1, username:"alice", email:"a@x.com"}     │
│  Row 2: {id:2, username:"bob", email:"b@x.com"}       │
│                                                        │
│  orders table (Key-Value):                            │
│  Row 1: {id:1, product:"laptop", qty:2}               │
│  Row 2: {id:2, product:"mouse", qty:5}                │
└────────────────────────────────────────────────────────┘
```

---

## 7. Multi-User Support

```
DATABASE WITH 3 USERS
─────────────────────────────────────────────────────

USER: alice
Timeline: CP1 ──► CP2 ──► CP3 ──► CP4
                           ▲
                           │
                    Current: CP3

USER: bob
Timeline: CP1 ──► CP5 ──► CP6
                  ▲
                  │
           Current: CP5

USER: charlie
Timeline: CP1 ──► CP7
          ▲
          │
   Current: CP1

Each user maintains:
┌─────────────────────────────────┐
│ user_checkpoint_position        │
├──────────┬─────────────────────┤
│ user_id  │ current_checkpoint  │
├──────────┼─────────────────────┤
│ alice    │ 3                   │
│ bob      │ 5                   │
│ charlie  │ 1                   │
└──────────┴─────────────────────┘

Checkpoints are USER-SCOPED:
┌──────────────────────────────────────────┐
│ checkpoint_metadata                      │
├────┬────────────────┬─────────┬─────────┤
│ id │ name           │ user_id │ parent  │
├────┼────────────────┼─────────┼─────────┤
│ 1  │ initial        │ alice   │ NULL    │
│ 2  │ after_edit     │ alice   │ 1       │
│ 3  │ final          │ alice   │ 2       │
│ 4  │ backup         │ alice   │ 3       │
│ 5  │ bob_start      │ bob     │ 1       │
│ 6  │ bob_v2         │ bob     │ 5       │
│ 7  │ charlie_test   │ charlie │ 1       │
└────┴────────────────┴─────────┴─────────┘
```

---

## 8. Storage Comparison

```
FORM_FIELDS TABLE - JSON STORAGE
──────────────────────────────────────────────────────
checkpoint_snapshots:
┌──────────────┬─────────────┬────────────────────────┐
│checkpoint_id │ table_name  │ snapshot_data (JSON)   │
├──────────────┼─────────────┼────────────────────────┤
│ 101          │form_fields  │ [                      │
│              │             │   {id:1, name:"email", │
│              │             │    props:{...}},       │
│              │             │   {id:2, name:"phone", │
│              │             │    props:{...}}        │
│              │             │ ]                      │
└──────────────┴─────────────┴────────────────────────┘
        ▲
        │ Entire table in ONE row
        │ Good for: Small tables, frequent bulk reads


USERS TABLE - KEY-VALUE STORAGE
──────────────────────────────────────────────────────
checkpoint_snapshots:
┌──────────────┬────────────┬────────┬─────────────────┐
│checkpoint_id │table_name  │row_id  │ row_data (JSON) │
├──────────────┼────────────┼────────┼─────────────────┤
│ 101          │ users      │ 1      │ {id:1, user:..} │
│ 101          │ users      │ 2      │ {id:2, user:..} │
│ 101          │ users      │ 3      │ {id:3, user:..} │
└──────────────┴────────────┴────────┴─────────────────┘
        ▲
        │ Each row stored separately
        │ Good for: Large tables, selective restore
```

---

## Key Benefits of This Architecture

✅ **Persistent** - Survives server restarts, connection drops
✅ **Multi-user** - Each user has independent checkpoint history
✅ **Complete DB capture** - All tables automatically included
✅ **Time travel** - Undo/redo/revert to any point
✅ **Transactional** - All operations are ACID compliant
✅ **Flexible** - JSON blob OR key-value storage per table

## Trade-offs

❌ **Storage overhead** - Each checkpoint stores full DB snapshot
❌ **Performance** - Large DBs = slower checkpoint creation
❌ **No incremental** - Cannot store just deltas (in this version)

Would you like me to proceed with this implementation?
