# OMS Order Flow Documentation

## Overview

This document describes the end-to-end lifecycle of an order through the Order Management System (OMS), from the moment a publisher encodes and sends an order to the final market gateway response.

The system is built around three key technologies:
- **Aeron** – ultra-low-latency IPC messaging transport
- **SBE (Simple Binary Encoding)** – zero-copy, fixed-length binary serialization
- **LMAX Disruptor** – lock-free, high-throughput inter-thread event queue

---

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              ORDER FLOW                                              │
│                                                                                      │
│  ┌─────────────────┐        ┌──────────────────┐       ┌──────────────────────┐     │
│  │  OrderPublisher │        │   Aeron Media    │       │   OrderSubscriber    │     │
│  │     or          │──SBE──▶│     Driver       │──IPC─▶│   (Aeron client)     │     │
│  │ RandomOrderPub  │        │  (MediaDriver    │       │                      │     │
│  │                 │        │    Main)         │       └────────┬─────────────┘     │
│  └─────────────────┘        └──────────────────┘                │                   │
│                                                                  │ publish()         │
│                                                          ┌───────▼─────────────┐     │
│                                                          │      OMSCore        │     │
│                                                          │   (Disruptor Ring   │     │
│                                                          │      Buffer)        │     │
│                                                          └───────┬─────────────┘     │
│                                                                  │ onEvent()         │
│                                                          ┌───────▼─────────────┐     │
│                                                          │  OrderEventHandler  │     │
│                                                          │  - Validation       │     │
│                                                          │  - Risk Check       │     │
│                                                          │  - GatewayListener  │     │
│                                                          └──┬────────────┬─────┘     │
│                                                             │            │           │
│                                              addOrder()     │            │ SBE       │
│                                                             │            │ OrderReq  │
│                                         ┌───────────────────▼──┐  ┌─────▼─────────┐ │
│                                         │    OMSOrderBook      │  │    Market      │ │
│                                         │  - BUY  price-desc   │  │    Gateway     │ │
│                                         │  - SELL price-asc    │  │  (simulated)   │ │
│                                         │  - matchOrders()     │  └──┬──┬──┬──┬──┘ │
│                                         └──────────────────────┘     │  │  │  │     │
│                                                                      │  │  │  │ SBE │
│                                              AckEvent ◄──────────────┘  │  │  │     │
│                                              RejectEvent ◄──────────────┘  │  │     │
│                                              FillEvent(s) ◄────────────────┘  │     │
│                                              DoneEvent ◄──────────────────────┘     │
│                                                                                      │
│                                         (fills arrive asynchronously at              │
│                                          configurable interval: gateway.fill.        │
│                                          interval.ms, default 50ms)                  │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Order Flow

### Step 1 – Media Driver Startup (`MediaDriverMain`)
**Class:** `com.satya.oms.driver.MediaDriverMain`

The Aeron **Media Driver** must be started first. It is a standalone process that manages:
- Shared memory IPC buffers (`aeron:ipc`)
- All publication and subscription log buffers

```
MediaDriverMain.main()
  └─ MediaDriver.launch()       ← starts the driver process, keeps running
```

**Configuration** (`oms.properties`):
```
aeron.channel=aeron:ipc?term-length=64k
aeron.stream.id=1001
```

---

### Step 2 – Order Encoding and Publishing (`OrderPublisher` / `RandomOrderPublisher`)
**Classes:** `com.satya.oms.aeron.OrderPublisher`, `com.satya.oms.aeron.RandomOrderPublisher`

The publisher:
1. Connects to the **already-running** Media Driver via `Aeron.connect(ctx)`
2. Creates a `Publication` on channel `aeron:ipc`, stream `1001`
3. Allocates a `DirectByteBuffer` and wraps it with `UnsafeBuffer` (zero-copy)
4. Uses **SBE** (`OrderEncoder` + `MessageHeaderEncoder`) to encode the order into the buffer
5. Calls `publication.offer(buffer, 0, length)` to send the message

**SBE Wire Format** (46 bytes of payload + 8-byte SBE message header):

| Offset | Size   | Field         | Type      |
|--------|--------|---------------|-----------|
| 0      | 8      | orderId       | uint64    |
| 8      | 4      | symbolId      | uint32    |
| 12     | 1      | side          | uint8 (BUY=0, SELL=1) |
| 13     | 8      | quantity      | uint64    |
| 21     | 8      | price         | uint64    |
| 29     | 1      | state         | uint8 (NEW=0, FILLED=1, PARTIALLY_FILLED=2, REJECTED=3) |
| 30     | 8      | filledQty     | uint64    |
| 38     | 8      | remainingQty  | uint64    |

**Key detail:** `RandomOrderPublisher` continuously sends random orders for a configurable duration (default 10 seconds), while `OrderPublisher` sends a single order then exits.

---

### Step 3 – IPC Transport via Aeron Media Driver
**Technology:** Aeron IPC (`aeron:ipc?term-length=64k`)

- The encoded buffer is passed to the Media Driver's **shared log buffer** in memory (no network, no kernel socket calls)
- `term-length=64k` sets the size of each term in the log buffer
- The Media Driver manages flow control and back-pressure; if the subscriber is slow, `publication.offer()` returns a negative value and the publisher backs off (`Thread.sleep(1)`)

---

### Step 4 – Receiving the Order (`OrderSubscriber`)
**Class:** `com.satya.oms.aeron.OrderSubscriber`

The subscriber runs on a **dedicated thread** (`Runnable`):
1. Connects to the same Media Driver and creates a `Subscription` on stream `1001`
2. Polls the subscription in a loop: `subscription.poll(fragmentHandler, 10)` — up to 10 fragments per poll
3. Uses `SleepingMillisIdleStrategy(1)` to back off when there are no messages
4. On each fragment, **manually reads the raw bytes** from the buffer at known SBE offsets (no SBE decoder used here — direct buffer reads for speed)
5. Calls `omsCore.publish(buffer, offset)` to hand off to the Disruptor

```
OrderSubscriber.run()
  └─ subscription.poll()
       └─ onFragment(buffer, offset, length, header)
            ├─ reads: orderId, symbolId, side, quantity, price, state
            └─ omsCore.publish(buffer, offset)   ← hands off to Disruptor
```

---

### Step 5 – Disruptor Ring Buffer (`OMSCore`)
**Class:** `com.satya.oms.disruptor.OMSCore`

`OMSCore` owns a **LMAX Disruptor** configured as:
- Ring buffer size: **1024 slots** (power of 2, configurable via `disruptor.ring.size`)
- Producer type: `MULTI` (supports multiple concurrent publishers)
- Wait strategy: `BlockingWaitStrategy` (consumer blocks when ring is empty)
- Event handler: `OrderEventHandler`

`publish()` claims the next sequence slot on the ring buffer and copies the raw bytes from the Aeron buffer into a pre-allocated `OrderEvent` (object reuse — no GC pressure).

```
OMSCore.publish(buffer, offset)
  └─ ringBuffer.publishEvent(
       (event, sequence) -> event.setBuffer(buffer, offset)
     )
```

**`OrderEvent`** stores a fixed 46-byte `UnsafeBuffer` copy of the SBE payload. Accessors like `getOrderId()`, `getSide()`, `getPrice()` read directly from this buffer.

---

### Step 6 – Order Processing (`OrderEventHandler`)
**Class:** `com.satya.oms.disruptor.OrderEventHandler`

This is the **single Disruptor consumer thread** and also implements `GatewayListener`. For each `OrderEvent` dequeued from the ring buffer, it performs three sequential steps:

#### 6a – Validation
```
if (quantity <= 0 || price <= 0)  → REJECTED (published immediately to Aeron)
```

#### 6b – Risk Check
```
if (quantity > 1000)              → REJECTED   (configurable via order.max.quantity)
```

#### 6c – Order Book + Matching
Valid orders proceed to the order book and matching engine.

#### 6d – SBE OrderRequest → Gateway
The handler encodes an SBE `OrderRequest` (message id=2) containing `{orderId, symbolId, side, quantity, price}` into a `DirectBuffer` and calls `gateway.sendOrder(buffer, offset, length, this)`.

No Java domain object crosses the handler–gateway boundary; only raw SBE bytes.

#### 6e – GatewayListener Callbacks
The handler implements `GatewayListener` and receives SBE-encoded responses:

| Callback    | Decodes          | Action                                                        |
|-------------|------------------|---------------------------------------------------------------|
| `onAck`     | `AckEvent`       | Log acknowledgement; fills will follow asynchronously         |
| `onReject`  | `RejectEvent`    | Remove in-flight state; publish REJECTED `Order` to Aeron     |
| `onFill`    | `FillEvent`      | Accumulate fill qty and record `{fillQty, fillPrice, execId}` |
| `onDone`    | `DoneEvent`      | Determine FILLED vs PARTIALLY_FILLED; publish final `Order` with all accumulated fills to Aeron |

Fill state is tracked per order in a `ConcurrentMap<Long, OrderFillState>` because fills arrive asynchronously from the gateway's scheduler thread.

---

### Step 7 – Order Book (`OMSOrderBook`)
**Class:** `com.satya.oms.core.OMSOrderBook`

Maintains two **sorted lists**:
- `buyOrders`  – sorted **descending** by price (highest bid first)
- `sellOrders` – sorted **ascending** by price (lowest ask first)

`addOrder(event)` inserts the order into the appropriate list and re-sorts.

`matchOrders()` attempts continuous crossing:
```
while top-buy.price >= top-sell.price:
    tradeQty = min(buy.qty, sell.qty)
    log "Matched: BuyID=X SellID=Y Qty=Z Price=P"
    reduce/remove quantities from both sides
```

---

### Step 8 – Market Gateway (`MarketGateway`)
**Class:** `com.satya.oms.gateway.MarketGateway`

Simulates sending the order to an external exchange. All communication uses SBE-encoded buffers via the `GatewayListener` callback interface.

**Flow:**
1. Receives an SBE `OrderRequest` buffer
2. Decodes using `OrderRequestDecoder`
3. Random decision: **90% Ack, 10% Reject**

| Step        | SBE Message     | Action                                                       |
|-------------|-----------------|--------------------------------------------------------------|
| Reject      | `RejectEvent`   | Encodes and calls `listener.onReject()` immediately; done    |
| Ack         | `AckEvent`      | Encodes and calls `listener.onAck()`                         |
| Fills       | `FillEvent`(s)  | Scheduled at `gateway.fill.interval.ms` (default 50ms); each tick sends a random partial fill via `listener.onFill()` |
| Done        | `DoneEvent`     | Sent once remaining quantity reaches 0; calls `listener.onDone()`; cancels schedule |

Fill generation uses a `ScheduledExecutorService`. Each fill is a random quantity between 1 and the remaining amount.

---

## Complete Flow Summary

```
[1] MediaDriverMain.main()
        │
        │  (Aeron IPC shared memory ready)
        │
[2] OrderPublisher / RandomOrderPublisher
        │  ┌─ SBE encode: OrderEncoder.wrapAndApplyHeader()
        │  └─ publication.offer(buffer, 0, length)
        │
        ▼
[3] Aeron Media Driver  ──  aeron:ipc  ──  stream 1001
        │
        ▼
[4] OrderSubscriber.onFragment()
        │  ├─ raw buffer read (orderId, symbolId, side, qty, price, state)
        │  └─ omsCore.publish(buffer, offset)
        │
        ▼
[5] OMSCore  →  Disruptor RingBuffer (1024 slots)
        │  └─ OrderEvent.setBuffer()  ← copies 46 bytes, zero GC
        │
        ▼
[6] OrderEventHandler.onEvent()
        │  ├─ [VALIDATION]   qty > 0, price > 0       →  REJECT if invalid
        │  ├─ [RISK CHECK]   qty <= 1000              →  REJECT if exceeded
        │  ├─ [ORDER BOOK]   orderBook.addOrder(event)
        │  ├─ [MATCHING]     orderBook.matchOrders()
        │  └─ [GATEWAY]      encode SBE OrderRequest  →  gateway.sendOrder(buf, this)
        │
        ▼
[7] OMSOrderBook
        │  ├─ BUY  list: sorted descending by price
        │  ├─ SELL list: sorted ascending  by price
        │  └─ Price-time crossing: buy.price >= sell.price → TRADE
        │
        ▼
[8] MarketGateway.sendOrder()
        │  ├─ Decode SBE OrderRequest
        │  ├─ 90%  → ACK  (AckEvent)   then async fills:
        │  │         ├─ FillEvent(s) at gateway.fill.interval.ms
        │  │         └─ DoneEvent when remaining = 0
        │  └─ 10%  → REJECT (RejectEvent)
        │
        ▼
   [GatewayListener callbacks → OrderEventHandler]
        │  ├─ onAck()    → log, await fills
        │  ├─ onReject() → publish REJECTED Order to Aeron
        │  ├─ onFill()   → accumulate filledQty in OrderFillState
        │  └─ onDone()   → publish final FILLED/PARTIALLY_FILLED Order to Aeron
```

---

## Key Design Decisions

| Concern             | Choice                          | Reason                                              |
|---------------------|---------------------------------|-----------------------------------------------------|
| Transport           | Aeron IPC                       | Shared memory, sub-microsecond latency, no TCP/UDP  |
| Serialization       | SBE                             | Zero-copy, fixed-layout, no heap allocation         |
| Inter-thread queue  | LMAX Disruptor                  | Lock-free ring buffer, ~10ns per slot vs ~1µs mutex |
| Buffer reuse        | Pre-allocated `OrderEvent`      | Object pooling via Disruptor — avoids GC pauses     |
| Order book sort     | `ArrayList` + `Comparator.sort` | Simple price-priority matching (FIFO within price)  |
| Gateway protocol    | SBE-encoded `DirectBuffer`s     | No Java objects cross handler–gateway boundary      |
| Gateway fills       | `ScheduledExecutorService`      | Async fill delivery at configurable interval        |
| Gateway             | Simulated (random)              | Stand-in for real FIX/exchange connectivity         |

---

## Class Reference

| Class                    | Package                        | Role                                              |
|--------------------------|--------------------------------|---------------------------------------------------|
| `MediaDriverMain`        | `...driver`                    | Standalone Aeron Media Driver process             |
| `OrderPublisher`         | `...aeron`                     | Single order publisher (demo/test)                |
| `RandomOrderPublisher`   | `...aeron`                     | Continuous random order publisher                 |
| `OrderSubscriber`        | `...aeron`                     | Aeron subscriber → Disruptor bridge               |
| `OMSCore`                | `...disruptor`                 | Disruptor owner; exposes `publish()`              |
| `OrderEvent`             | `...disruptor`                 | Reusable ring buffer slot (46-byte SBE payload)   |
| `OrderEventFactory`      | `...disruptor`                 | Pre-populates ring buffer slots at startup        |
| `OrderEventHandler`      | `...disruptor`                 | Consumer: validation → risk → book → gateway      |
| `GatewayListener`        | `...gateway`                   | Callback interface for SBE gateway responses      |
| `OMSOrderBook`           | `...core`                      | Price-sorted dual-sided order book + matcher      |
| `MarketGateway`          | `...gateway`                   | Simulated exchange connectivity                   |
| `OMSConfig`              | `...config`                    | Loads `oms.properties`; provides typed accessors  |

---

## SBE Schema (`sbe/order-schema.xml`)

```xml
<!-- Outbound order published on Aeron -->
<message name="Order" id="1">
  <field name="orderId"      id="1" type="uint64"/>
  <field name="symbolId"     id="2" type="uint32"/>
  <field name="side"         id="3" type="Side"/>        <!-- BUY=0, SELL=1 -->
  <field name="quantity"     id="4" type="uint64"/>
  <field name="price"        id="5" type="uint64"/>
  <field name="state"        id="6" type="OrderState"/>  <!-- NEW/FILLED/PARTIALLY_FILLED/REJECTED -->
  <field name="filledQty"    id="7" type="uint64"/>
  <field name="remainingQty" id="8" type="uint64"/>
  <group name="fills" ...>
    <field name="fillQty" /><field name="fillPrice" /><field name="executionId" />
  </group>
</message>

<!-- Handler → Gateway: new order request -->
<message name="OrderRequest" id="2">
  <field name="orderId"  id="1" type="uint64"/>
  <field name="symbolId" id="2" type="uint32"/>
  <field name="side"     id="3" type="Side"/>
  <field name="quantity" id="4" type="uint64"/>
  <field name="price"    id="5" type="uint64"/>
</message>

<!-- Gateway → Handler: order acknowledged -->
<message name="AckEvent" id="3">
  <field name="orderId" id="1" type="uint64"/>
</message>

<!-- Gateway → Handler: order rejected -->
<message name="RejectEvent" id="4">
  <field name="orderId"    id="1" type="uint64"/>
  <field name="rejectCode" id="2" type="uint32"/>
</message>

<!-- Gateway → Handler: a single fill -->
<message name="FillEvent" id="5">
  <field name="orderId"     id="1" type="uint64"/>
  <field name="fillQty"     id="2" type="uint64"/>
  <field name="fillPrice"   id="3" type="uint64"/>
  <field name="executionId" id="4" type="uint64"/>
</message>

<!-- Gateway → Handler: no more fills for this order -->
<message name="DoneEvent" id="6">
  <field name="orderId" id="1" type="uint64"/>
</message>
```

Byte order: **little-endian**. Order block length: **46 bytes** + 8-byte SBE `messageHeader`.
