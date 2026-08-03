# Aegis 🛡️

**Adaptive rate limiting & resilience gateway for the JVM — built from scratch, not `import`ed.**

![Java](https://img.shields.io/badge/Java-21-orange)
![Zero web framework](https://img.shields.io/badge/dependencies-JDK%20only-informational)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**[🔴 Live demo]([https://your-app.onrender.com](https://aegis-mtjq.onrender.com/))** — click *Start burst* and watch the rate limiter shed traffic and the circuit breaker trip in real time.

![Aegis dashboard](docs/dashboard.png)

## What it is

Three rate-limiting algorithms, a three-state circuit breaker, and a live
Server-Sent-Events dashboard — the exact patterns behind Stripe's API
limits and Netflix's Hystrix — implemented by hand so the concurrency
correctness is something I can defend in an interview, not something I
called a library for.

- **Token bucket** — lock-free, dual `AtomicLong` CAS loops
- **Sliding window log** — exact, narrowly-`synchronized` critical section
- **Sliding window counter** — `ConcurrentHashMap` + CAS, O(1) memory, smooths window-boundary bursts
- **Circuit breaker** — closed → open → half-open, atomic state transitions, no lock
- **Strategy + Factory + Builder** throughout — swapping algorithms is a one-line config change
- **Zero framework** — the demo server is the JDK's own `com.sun.net.httpserver`
- **Stress-tested** — a `CountDownLatch`-synchronized test hammers each algorithm with 400 concurrent acquires against a limit of 100 and asserts *exactly* 100 get through

## Quick start

```bash
git clone https://github.com/<your-username>/aegis.git
cd aegis
mvn compile exec:java
```

Open `http://localhost:8080` → **Start burst**. Or just run `mvn test`.

## Architecture

```
core           RateLimiter strategy interface + config + factory
algorithms     TokenBucket · SlidingWindowLog · SlidingWindowCounter
circuitbreaker Closed/Open/HalfOpen state machine
gateway        AegisGateway — one call() facade over both
dashboard      Embedded HTTP server + SSE metrics stream
```

## Algorithms, side by side

| | Memory | Precision | Boundary bursts |
|---|---|---|---|
| Token bucket | O(1) | Approximate | Full burst allowed instantly, then throttled |
| Sliding window log | O(limit) | Exact | None — real timestamps |
| Sliding window counter | O(1) | Approximate | Smoothed via weighted blend of two windows |

## Deploy your own

```bash
docker build -t aegis .
docker run -p 8080:8080 aegis
```

Ships with a `Dockerfile`, `render.yaml`, and GitHub Actions CI — push to
GitHub and connect the repo on [Render](https://render.com), [Railway](https://railway.app),
or [Fly.io](https://fly.io) for a free, zero-config live URL.

## License

MIT — see [LICENSE](LICENSE).
