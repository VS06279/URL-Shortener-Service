# URL Shortener Service

A link-shortening REST API in Java / Spring Boot. It generates short links, tracks
click analytics per link, and applies per-client rate limiting so one heavy caller
cannot degrade response times for everyone else.

Built as a link-management backend for campaign and donation links, where the same
destination gets shared across email, SMS, and social, and each channel needs its
own trackable link.

## Running it

Requires JDK 17+ and Maven.

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Two demo API keys are configured in
`src/main/resources/application.yml`: `demo-key-akshaya` and `demo-key-partner`.

```bash
mvn test        # run the test suite
mvn package     # build target/url-shortener-1.0.0.jar
docker build -t url-shortener .
```

## API

Every `/api/**` request needs an `X-API-Key` header. The key resolves to a client id,
and clients only ever see their own links.

### Create a link

```bash
curl -X POST http://localhost:8080/api/v1/links \
  -H "X-API-Key: demo-key-akshaya" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.akshayapatra.org/donate", "alias": "give-2026", "expiresInDays": 90}'
```

```json
{
  "code": "give-2026",
  "shortUrl": "http://localhost:8080/give-2026",
  "targetUrl": "https://www.akshayapatra.org/donate",
  "createdAt": "2026-09-06T14:02:11Z",
  "expiresAt": "2026-12-05T14:02:11Z"
}
```

`alias` and `expiresInDays` are optional. Without an alias, a random 7-character
base62 code is generated.

### Follow a link

```bash
curl -i http://localhost:8080/give-2026
# HTTP/1.1 302 Found
# Location: https://www.akshayapatra.org/donate
```

### Read analytics

```bash
curl http://localhost:8080/api/v1/links/give-2026/stats \
  -H "X-API-Key: demo-key-akshaya"
```

```json
{
  "code": "give-2026",
  "totalClicks": 1284,
  "uniqueVisitors": 902,
  "lastClickAt": "2026-09-06T18:44:02Z",
  "clicksByDay": { "2026-09-05": 611, "2026-09-06": 673 },
  "topReferrers": { "t.co": 508, "direct": 402, "mail.google.com": 374 }
}
```

### Other endpoints

| Method | Path                          | Description                        |
|--------|-------------------------------|------------------------------------|
| GET    | `/api/v1/links`               | List the calling client's links    |
| GET    | `/api/v1/links/{code}`        | Link metadata                      |
| GET    | `/api/v1/links/{code}/stats`  | Click analytics                    |
| DELETE | `/api/v1/links/{code}`        | Delete a link                      |
| GET    | `/actuator/health`            | Health check                       |

## Rate limiting

Each client gets its own token bucket: a burst capacity plus a steady refill rate,
both configurable.

```yaml
shortener:
  rate-limit:
    capacity: 60          # burst size
    refill-per-minute: 60 # sustained rate
```

Every API response carries `X-RateLimit-Limit` and `X-RateLimit-Remaining`. Over the
limit returns `429` with a `Retry-After` header.

Refill is computed lazily from the clock on each request rather than by a background
timer, so idle clients cost nothing and the check stays O(1). Buckets are held in a
`ConcurrentHashMap` and locking is per-client, so two clients never contend.

Public redirects are not throttled — they are the read path real users hit, and the
work per request is a single map lookup.

## Design notes

**Clicks are recorded off the request thread.** The redirect handler pulls the few
headers it needs, hands them to a bounded executor queue, and returns. Analytics
writes never sit in the user's latency path, and if the queue ever fills, events are
dropped rather than allowed to back up the request thread.

**Counters are aggregated on write.** Rather than storing every raw click and
aggregating at read time, each link keeps atomic counters for total clicks, clicks per
UTC day, and clicks per referrer host. Reading stats stays cheap no matter how popular
a link gets. Every collection is capped, so a viral link cannot grow memory without
bound.

**Codes are random, not sequential.** Sequential ids would let anyone enumerate the
whole link set by counting up and see other clients' destinations. Random base62
codes are generated with `SecureRandom` and retried on collision.

**Redirects are 302, not 301.** A permanent redirect gets cached by the browser
forever, which silently stops click tracking and makes the link impossible to
retarget later.

**Visitors are hashed, not stored.** Unique-visitor counts use a per-process salted
SHA-256 of the IP, so the number is useful without retaining an address.

**A link you don't own returns 404, not 403.** A 403 would confirm the code exists.

## Structure

```
api/         controllers, API key filter, error handling
api/dto/     request and response bodies
ratelimit/   token bucket and the filter that applies it
service/     link creation, resolution, click recording
store/       repository interface + in-memory implementation
domain/      Link, ClickEvent, ClickContext
config/      configuration properties
```

## Swapping the storage layer

Everything goes through the `LinkRepository` interface. `InMemoryLinkRepository`
is the current implementation; a production deployment would put links in Postgres,
counters in Redis (`INCR` on a per-link key), and the rate limit buckets in Redis too
so limits hold across multiple instances. No controller or service code changes.
