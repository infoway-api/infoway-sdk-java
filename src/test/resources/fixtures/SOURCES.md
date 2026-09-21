# Fixture provenance

Every file here is a **verbatim capture from the production API** (`https://data.infoway.io`),
taken 2026-08-15 from the public API. No hand-written / invented payloads —
that is exactly the defect that made the previous unit tests green but worthless.

| File | How it was captured |
|------|---------------------|
| `rest/trade_stock.json` | `GET /stock/batch_trade/AAPL.US` |
| `rest/trade_crypto.json` | `GET /crypto/batch_trade/BTCUSDT` |
| `rest/kline_stock.json` | `POST /stock/v2/batch_kline {"codes":"AAPL.US","klineType":1,"klineNum":2}` |
| `rest/depth_stock.json` | `GET /stock/batch_depth/AAPL.US` |
| `rest/symbols.json` | `GET /common/basic/symbols?type=STOCK_US&symbols=AAPL.US` |
| `rest/symbol_info.json` | `GET /common/basic/symbols/info?type=STOCK_US&symbols=AAPL.US` |
| `rest/adjustment_factors.json` | `GET /common/basic/symbols/adjustment_factors?symbol=AAPL.US&market=US&beginDay=20260801&endDay=20260815` |
| `rest/trading_days.json` | `GET /common/basic/markets/trading_days?market=US&beginDay=20260801&endDay=20260815` |
| `rest/trading_schedule_first_entry.json` | `GET /common/basic/markets/trading_schedule` — real response, **truncated to `data[0]`** (full body is 555 KB). No field was added or renamed. |
| `rest/plate_intro_no_data_key.json` | `GET /common/v2/basic/plate/intro/IN20293.HK` — envelope **without** a `data` key |
| `rest/plate_industry_v2_envelope.json` | `GET /common/v2/basic/plate/industry/HK?limit=2` — `{market,count,data}` envelope |
| `rest/market_breadth_v2_envelope.json` | `GET /common/v2/basic/market/breadth/US` — `{market,data}` envelope |
| `rest/err_400_rfc7807.json` | `GET /common/basic/symbols` with no `type` → HTTP 400 problem+json |
| `rest/err_401.json` | any REST path with an invalid key → HTTP 401, note the field is `message`, not `msg` |
| `rest/err_rate_limit.json` | gateway rate-limit body (`{"detail":"Rate limit exceeded"}`); observed on HTTP 429 **and** on HTTP 200 |
| `ws/welcome.json`, `ws/sub_depth_ack.json`, `ws/sub_kline_ack.json`, `ws/push_depth.json`, `ws/push_kline.json` | extracted verbatim from a recorded crypto WebSocket session (`_raw` field) |
| `ws/push_trade.json`, `ws/plaintext_greeting.txt`, `ws/error_507.json` | verbatim frames recorded in the audit's WS baseline (`ws_schema.md` §3.1 / §1.1 / §5.1) |
| `ws/news_push_docs_derived.json` | **the one exception**: the audit key has no `newsFlag`, so `/news` answers HTTP 401 (verified again 2026-08-15) and no live push could be captured. Field set is taken from the official contract table (`dk/country/lang/route/title/published/urgency/provider/symbols/link/content/sd`). Replace with a real capture as soon as a news-enabled key exists. |
