# Reference backend (optional, recommended for production)

`cloudflare-worker/worker.js` is a ~120-line Cloudflare Worker that keeps secrets off the phone:

| Route | Purpose |
|---|---|
| `GET /naver/callback` | Register `https://<worker>/naver/callback` as the NAVER Login **Callback URL**. It bounces `code`/`state` to `aistudio://oauth/naver`. |
| `POST /naver/token` | Exchanges/refreshes/revokes NAVER tokens using the client secret stored in the Worker. Set the app's `NAVER_TOKEN_EXCHANGE_URL` to this. |
| `GET /v1/models`, `POST /v1/messages` | Claude proxy. The Anthropic API key stays in the Worker. Set the app's AI connection to **백엔드 프록시** with `AI_PROXY_BASE_URL=https://<worker>`. |

```bash
cd backend/cloudflare-worker
cp wrangler.toml.example wrangler.toml
npx wrangler secret put NAVER_CLIENT_ID
npx wrangler secret put NAVER_CLIENT_SECRET
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put APP_TOKENS      # e.g. a long random string; enter the same value in the app (설정 → AI → 프록시 앱 토큰)
npx wrangler deploy
```

This is a reference implementation: add per-user auth, quotas and logging policies before exposing it publicly.
The app works without it (developer mode: key entered on-device, stored with Android Keystore).
