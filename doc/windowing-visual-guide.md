# Stream Processing Windows: Visual Guide

This guide uses visual diagrams to help understand windowing concepts.

## Table of Contents
1. [Window Types](#window-types)
2. [Event Time vs Processing Time](#event-time-vs-processing-time)
3. [Watermarks](#watermarks)
4. [Late Data Handling](#late-data-handling)
5. [Window Lifecycle](#window-lifecycle)

---

## Window Types

### Tumbling Windows
**Fixed-size, non-overlapping windows**

```
Timeline (minutes):
0    5    10   15   20   25   30
├────┼────┼────┼────┼────┼────┤
│ W1 │ W2 │ W3 │ W4 │ W5 │ W6 │
└────┴────┴────┴────┴────┴────┘

Events:
    ● ●   ●     ●  ●    ●     ●
    └─┘   │     └──┘    │     │
     W1   W2     W3     W5    W6

Each event belongs to exactly ONE window
No overlap between windows
```

**Use Cases:**
- Hourly/daily reports
- Batch-like processing
- Fixed interval metrics

**Example:** Count events every 5 minutes
```
Window 1 [00:00-00:05]: 150 events
Window 2 [00:05-00:10]: 173 events
Window 3 [00:10-00:15]: 162 events
```

---

### Sliding Windows
**Fixed-size, overlapping windows**

```
Window Size: 10 minutes
Slide: 5 minutes

Timeline (minutes):
0    5    10   15   20   25   30
├────┼────┼────┼────┼────┼────┤
│─────W1─────│
     │─────W2─────│
          │─────W3─────│
               │─────W4─────│
                    │─────W5─────│

Events:
    ●     ●     ●     ●     ●
    │     ├─────┤     │     │
    W1    W1,W2 W2,W3 W3,W4 W4,W5

Events appear in MULTIPLE windows
Number of windows = window_size / slide_interval
```

**Use Cases:**
- Moving averages
- Trend detection
- Smoothing metrics

**Example:** 10-minute average, updated every 2 minutes
```
Window 1 [00:00-00:10]: avg = 50
Window 2 [00:02-00:12]: avg = 52  (smooth transition)
Window 3 [00:04-00:14]: avg = 55
```

**Trade-off:**
```
More Overlap = More Computation = Smoother Metrics

slide = window_size → Tumbling window (no overlap)
slide < window_size → Overlapping windows
slide > window_size → Gaps (some events not counted)
```

---

### Session Windows
**Dynamic, gap-based windows**

```
Gap Timeout: 30 minutes

User A's Activity:
Time:    0    10   20   30   40   50   60   70   80
Events:  ●    ●    ●              ●         ●    ●
         │<---Session 1--->│    30min gap   │<-S2->│
         └──────────────────┘                └──────┘

Session 1: 00:00 - 00:20 (3 events)
  - Closes at 00:50 (30 min after last event at 00:20)

Session 2: 01:00 - 01:20 (3 events)
  - Currently active (waiting for 30 min timeout)
```

**Session Merging:**
```
Initial State:
    ●        ●
    │<-gap->│

Event arrives in gap:
    ●   ●    ●
    │<-Session-│  (merged into one)
```

**Use Cases:**
- User session analytics
- Shopping sessions
- Device connection patterns

**Example: E-commerce Session**
```
Session ID: user123-1698765432000
Start: 10:00:00
Events:
  10:00:00 - page_view (landing page)
  10:02:30 - page_view (product page)
  10:05:00 - click (add to cart)
  10:07:15 - page_view (checkout)
  10:10:30 - click (purchase)
End: 10:10:30
Duration: 10.5 minutes
Session closes: 10:40:30 (30 min after last event)
```

---

## Event Time vs Processing Time

### The Problem
```
What Really Happened (Event Time):
10:00:00  10:00:05  10:00:10  10:00:15
   E1       E2        E3        E4
   │        │         │         │

When System Saw It (Processing Time):
   ┌────────┼─────────┤         │
   │        │         │         │
10:05:00  10:04:30  10:06:00  10:04:00
   E1       E4        E2        E3

Order:
  Processing Time: E4 → E3 → E1 → E2  ❌ Wrong for analytics!
  Event Time:      E1 → E2 → E3 → E4  ✅ Correct!
```

### Why Events Arrive Out-of-Order

```
Mobile User:
    Phone                Network           Server
      │                     │                 │
  10:00│ Event occurs       │                 │
      │────────────────────>│ Network delay   │
      │                   10:03               │
      │                     │────────────────>│
      │                     │              10:05
      │                     │          Arrives late!
```

### Event Time Windows Handle This

```
Window [10:00 - 10:05]

Processing Order:    Event Time Order:
    E3 (10:00:10)       E1 (10:00:00) ──┐
    E1 (10:00:00)       E2 (10:00:03) ──┤ All go to
    E4 (10:00:15)   →   E3 (10:00:10) ──┤ same window
    E2 (10:00:03)       E4 (10:00:15) ──┘

Result: Correct count of 4 events in [10:00-10:05] window
```

---

## Watermarks

### What is a Watermark?

```
Watermark = "No events with timestamp < W will arrive"

Example:
Current watermark: 10:10:00

Meaning: All events with event_time < 10:10:00 have arrived
         (or we're willing to ignore any that haven't)
```

### Watermark Strategy

```
Bounded Out-of-Orderness:
maxOutOfOrderness = 10 minutes

Timeline:
Event Time:    10:00  10:05  10:10  10:15  10:20
Max Seen:        |      |      |      |      |
Watermark:    (none) 09:50  09:55  10:00  10:05  10:10

Watermark = Max Event Time Seen - maxOutOfOrderness

At 10:20 event time:
  Max seen: 10:20
  Watermark: 10:20 - 10 min = 10:10

"All events before 10:10 have arrived (probably)"
```

### Watermark and Window Closure

```
Window [10:00 - 10:05]

Events arriving:
10:05 (processing)  Event(10:02) → Add to window
10:06 (processing)  Event(10:04) → Add to window
10:07 (processing)  Event(10:08) → Watermark advances to 09:58
10:12 (processing)  Event(10:14) → Watermark advances to 10:04
                                   Window [10:00-10:05] still open
10:18 (processing)  Event(10:17) → Watermark advances to 10:07
                                   🔒 Window [10:00-10:05] CLOSES
                                   Results emitted!

Window closes when: Watermark >= Window.End
```

### Choosing maxOutOfOrderness

```
Trade-off:

Small maxOutOfOrderness (e.g., 1 minute):
  ✅ Low latency (windows close quickly)
  ❌ More late data dropped

Large maxOutOfOrderness (e.g., 1 hour):
  ✅ Handles very late data
  ❌ High latency (windows stay open longer)

Optimal = Analyze your data's lateness distribution!
```

---

## Late Data Handling

### Three Categories of Events

```
Window [10:00 - 10:05]
maxOutOfOrderness: 10 minutes
allowedLateness: 5 minutes

Timeline:
         Window      Watermark      Allowed Lateness
           End       Reaches End       Expires
            │            │               │
10:00   10:05        10:15           10:20
├───────┼────────────┼───────────────┼────────>
│       │            │               │
│ Active│  Late but  │   Too Late    │
│Window │  Allowed   │   (Dropped)   │
└───────┴────────────┴───────────────┘

Event arrives at:
  10:10 (event_time=10:03) → ✅ ON-TIME (before watermark)
  10:16 (event_time=10:04) → ⚠️  LATE (after watermark, within allowed lateness)
  10:22 (event_time=10:02) → ❌ TOO LATE (after allowed lateness)
```

### Late Event Flow

```
                    Event Arrives
                         │
                         ▼
              ┌──────────────────────┐
              │ Check Watermark      │
              └──────────┬───────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
         ▼               ▼               ▼
    On-Time         Late but       Too Late
    (normal)        Allowed        (side output)
         │               │               │
         ▼               ▼               ▼
    Add to         Re-compute      Send to late
    window         window          events sink
         │               │
         └───────┬───────┘
                 ▼
           Emit results
```

### Example: Late Data in Action

```
Scenario:
- Window: [10:00 - 10:05]
- maxOutOfOrderness: 10 min
- allowedLateness: 5 min

Events Stream:
Time  | Event Time | Watermark | Window State            | Action
------|------------|-----------|-------------------------|--------
10:08 | 10:02      | 09:52     | Active, count=1         | Add
10:09 | 10:03      | 09:53     | Active, count=2         | Add
10:16 | 10:14      | 10:04     | Active, count=2         | Wait
10:18 | 10:15      | 10:05     | 🔒 CLOSED, emit count=2 | Emit
10:19 | 10:04      | 10:05     | Reopened! count=3       | Re-compute & emit
10:24 | 10:03      | 10:09     | Still open, count=4     | Re-compute & emit
10:28 | 10:20      | 10:10     | Window expired          | Window truly closed
10:30 | 10:02      | 10:10     | Too late!               | → Side output

Side Output Record:
{
  event_time: 10:02,
  arrival_time: 10:30,
  lateness: 28 minutes,
  reason: "After allowed lateness"
}
```

---

## Window Lifecycle

### Complete Lifecycle Diagram

```
┌────────────────────────────────────────────────────────────┐
│                  Window [10:00 - 10:05]                    │
└────────────────────────────────────────────────────────────┘

Phase 1: ACTIVE
├─────────────────────────────────────┐
│ Watermark < Window.End (10:05)      │
│                                     │
│ ● Events added to window            │
│ ● State accumulated                 │
│ ● No output yet                     │
│                                     │
│ Duration: Depends on data arrival   │
└─────────────────────────────────────┘
         │
         │ Watermark reaches 10:05
         ▼
Phase 2: LATE (if allowed lateness configured)
├─────────────────────────────────────┐
│ 10:05 <= Watermark < 10:10          │
│                                     │
│ ● First results emitted             │
│ ● Late events can still arrive     │
│ ● Window re-computed and updated   │
│ ● Multiple outputs possible         │
│                                     │
│ Duration: allowedLateness period    │
└─────────────────────────────────────┘
         │
         │ Watermark reaches 10:10
         ▼
Phase 3: CLOSED
├─────────────────────────────────────┐
│ Watermark >= 10:10                  │
│                                     │
│ ● Window state discarded            │
│ ● Late events → side output         │
│ ● No more updates                   │
│                                     │
│ Duration: Forever                   │
└─────────────────────────────────────┘
```

### State Management

```
Window State Over Time:

T=10:08 (Active)
┌──────────────────┐
│ Window [10:00-05]│
│ Count: 5         │
│ Sum: 127         │
│ State Size: 2KB  │
└──────────────────┘

T=10:16 (First Emit)
┌──────────────────┐
│ Window [10:00-05]│ ──────> OUTPUT: count=5, sum=127
│ Count: 5         │         (Watermark passed 10:05)
│ Sum: 127         │
│ State Size: 2KB  │ (kept for late data)
└──────────────────┘

T=10:19 (Late Update)
┌──────────────────┐
│ Window [10:00-05]│ ──────> OUTPUT: count=7, sum=150
│ Count: 7         │         (Late events arrived)
│ Sum: 150         │
│ State Size: 2KB  │
└──────────────────┘

T=10:28 (Closed)
┌──────────────────┐
│   [DELETED]      │         State cleaned up
└──────────────────┘         Memory freed
```

---

## Real-World Example: Session Analytics

### User Journey Visualization

```
User: alice@email.com
Session Timeout: 30 minutes

Activity Timeline:
10:00:00    10:05:00    10:10:00    10:40:00    10:45:00    10:50:00
    │           │           │           │           │           │
    ● landing   ● product   ● cart      │           ● landing   ● product
    page        page        added       │           page        page
                                        │
                            30 min gap  │
                                        │
    │<────── Session 1 ────────────────│
    Start: 10:00                        │
    End: 10:10                          │
    Duration: 10 minutes                │
    Events: 3                           │
                                        │
                            Session closes at 10:40 (timeout)
                                        │
                                        │
                                        │<─── Session 2 ───>
                                            Start: 10:45
                                            End: 10:50
                                            Duration: 5 min
                                            Events: 2
```

### Session Window Processing

```
Stream of Events:
Event 1: alice, 10:00, landing
    → New session created: alice-session-1

Event 2: alice, 10:05, product
    → Added to alice-session-1

Event 3: alice, 10:10, cart
    → Added to alice-session-1

[30 minute gap - no events]

Event 4: alice, 10:45, landing
    → alice-session-1 closed (timeout reached)
    → Output: {start: 10:00, end: 10:10, events: 3, duration: 10min}
    → New session created: alice-session-2

Event 5: alice, 10:50, product
    → Added to alice-session-2
```

### Session Merging Example

```
Scenario: Event arrives that fills a gap

Initial State:
alice, 10:00, landing → Session-A [10:00-10:00]
[20 minute gap]
alice, 10:20, product → Session-B [10:20-10:20]

Two separate sessions (gap > 30 min? No, only 20 min)

Late Event Arrives:
alice, 10:10, cart (event time between Session-A and B)

Result: Sessions merge!
Session-A+B [10:00-10:20]
  - landing (10:00)
  - cart (10:10)
  - product (10:20)

If gap was > 30 min:
Session-A [10:00-10:00] - closed
Session-B [10:40-10:40] - separate session
```

---

## Performance Considerations

### State Size Growth

```
Window Type | State Growth | When to Use
------------|--------------|-------------
Tumbling    | ████         | Always safe
Sliding     | ████████     | Medium state ok
Session     | ████████████ | Careful! Can grow large

Session State Example:
User with 1000 events in 24-hour session:
  - All events buffered
  - State size = events * event_size
  - Use AggregateFunction for incremental aggregation!
```

### Watermark Lag

```
Healthy:
Events     ●●●●●●●●●●●●●●●●●●●●
Watermark      ═════════════════

Lag: ~10 minutes (controlled)


Unhealthy:
Events     ●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●
Watermark      ══════════

Lag: Growing! Windows don't close!

Causes:
- Events with very old timestamps
- Clock skew
- Replay of old data
```

---

## Quick Decision Tree

```
                  Which Window Type?
                         │
           ┌─────────────┼─────────────┐
           │             │             │
      Fixed         Overlapping    Dynamic/
      intervals?    windows?       Gap-based?
           │             │             │
           ▼             ▼             ▼
      TUMBLING       SLIDING       SESSION

Examples:
  Hourly reports  → Tumbling
  Moving average  → Sliding
  User sessions   → Session
  Alert on spike  → Sliding (detect trend)
  Daily batch     → Tumbling
  Shopping cart   → Session
```

---

## Summary Cheat Sheet

```
╔══════════════════════════════════════════════════════════════╗
║                    WINDOW TYPE SUMMARY                       ║
╠═══════════════════╦══════════════╦═══════════════════════════╣
║ TUMBLING          ║ SLIDING      ║ SESSION                   ║
╠═══════════════════╬══════════════╬═══════════════════════════╣
║ Fixed size        ║ Fixed size   ║ Dynamic size              ║
║ No overlap        ║ Overlap      ║ No overlap                ║
║ Time-based        ║ Time-based   ║ Gap-based                 ║
║ Low state         ║ Medium state ║ High state (can grow)     ║
║ Predictable       ║ Predictable  ║ Unpredictable             ║
╠═══════════════════╬══════════════╬═══════════════════════════╣
║ Reports           ║ Trends       ║ User behavior             ║
║ Metrics           ║ Smoothing    ║ Shopping carts            ║
║ Batch-like        ║ Anomalies    ║ Device connections        ║
╚═══════════════════╩══════════════╩═══════════════════════════╝

Event Time:    When event occurred (in the real world)
Processing Time: When system processes event
Ingestion Time: When event enters streaming system

Watermark:     Tracks progress in event time
               = max_event_time - maxOutOfOrderness

Late Data:     Event arriving after watermark passes
               Handle with allowedLateness + side output

Window Closes: When watermark >= window.end (+ allowed lateness)
```

This visual guide should help you understand the concepts before diving into the code exercises!
