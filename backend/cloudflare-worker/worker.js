/**
 * NAVER Shopping Connect AI Studio — reference backend (Cloudflare Worker).
 *
 * Keeps every server secret OUT of the APK:
 *   - NAVER OAuth client secret  (token exchange / refresh / revoke)
 *   - Anthropic API key          (Claude Messages + Models proxy)
 *
 * Routes
 *   GET  /naver/callback   NAVER redirects here (register this https URL as the Callback URL);
 *                          bounces code+state to the app: aistudio://oauth/naver?code=…&state=…
 *   POST /naver/token      form: grant_type=authorization_code|refresh_token|delete, code, state, refresh_token, access_token
 *   GET  /v1/models        proxied to api.anthropic.com
 *   POST /v1/messages      proxied to api.anthropic.com
 *
 * Environment (wrangler secrets / vars):
 *   NAVER_CLIENT_ID, NAVER_CLIENT_SECRET, ANTHROPIC_API_KEY,
 *   APP_TOKENS   comma-separated bearer tokens your app installs send (optional but recommended),
 *   ALLOWED_MODELS  comma-separated model allowlist (optional), MAX_TOKENS_CAP (optional, default 32000)
 */

const APP_CALLBACK = "aistudio://oauth/naver";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/naver/callback" && request.method === "GET") return naverCallback(url);
      if (url.pathname === "/naver/token" && request.method === "POST") return naverToken(request, env);
      if (url.pathname === "/v1/models" && request.method === "GET") return claudeProxy(request, env, "/v1/models" + url.search);
      if (url.pathname === "/v1/messages" && request.method === "POST") return claudeProxy(request, env, "/v1/messages");
      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({ error: "proxy_error" }, 502);
    }
  },
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
}

function naverCallback(url) {
  const code = url.searchParams.get("code");
  const state = url.searchParams.get("state");
  const error = url.searchParams.get("error");
  const target = new URL(APP_CALLBACK);
  if (error) target.searchParams.set("error", error);
  if (code) target.searchParams.set("code", code);
  if (state) target.searchParams.set("state", state);
  const href = target.toString();
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>AI Studio</title><p>앱으로 돌아가는 중…</p><p><a href="${href.replace(/"/g, "&quot;")}">앱 열기</a></p>
<script>location.replace(${JSON.stringify(href)});</script>`;
  return new Response(html, { status: 200, headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "referrer-policy": "no-referrer" } });
}

async function naverToken(request, env) {
  const form = await request.formData();
  const grant = form.get("grant_type");
  if (!["authorization_code", "refresh_token", "delete"].includes(grant)) return json({ error: "invalid_grant_type" }, 400);
  const q = new URLSearchParams({ grant_type: grant, client_id: env.NAVER_CLIENT_ID, client_secret: env.NAVER_CLIENT_SECRET });
  for (const k of ["code", "state", "refresh_token", "access_token", "service_provider"]) {
    const v = form.get(k);
    if (v) q.set(k, v);
  }
  const r = await fetch("https://nid.naver.com/oauth2.0/token?" + q.toString(), { method: "POST" });
  const body = await r.text();
  return new Response(body, { status: r.status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
}

function authorized(request, env) {
  const tokens = (env.APP_TOKENS || "").split(",").map((s) => s.trim()).filter(Boolean);
  if (tokens.length === 0) return true; // open proxy — only for private testing
  const auth = request.headers.get("authorization") || "";
  return tokens.some((t) => auth === `Bearer ${t}`);
}

async function claudeProxy(request, env, path) {
  if (!authorized(request, env)) return json({ type: "error", error: { type: "authentication_error", message: "invalid app token" } }, 401);
  const headers = new Headers({
    "x-api-key": env.ANTHROPIC_API_KEY,
    "anthropic-version": request.headers.get("anthropic-version") || "2023-06-01",
    "content-type": "application/json",
  });
  const beta = request.headers.get("anthropic-beta");
  if (beta) headers.set("anthropic-beta", beta);
  let body;
  if (request.method === "POST") {
    const payload = await request.json();
    const allowed = (env.ALLOWED_MODELS || "").split(",").map((s) => s.trim()).filter(Boolean);
    if (allowed.length && !allowed.includes(payload.model)) {
      return json({ type: "error", error: { type: "not_found_error", message: `model: ${payload.model}` } }, 404);
    }
    const cap = parseInt(env.MAX_TOKENS_CAP || "32000", 10);
    if (payload.max_tokens > cap) payload.max_tokens = cap;
    body = JSON.stringify(payload);
  }
  const r = await fetch("https://api.anthropic.com" + path, { method: request.method, headers, body });
  return new Response(r.body, { status: r.status, headers: { "content-type": r.headers.get("content-type") || "application/json", "retry-after": r.headers.get("retry-after") || "" } });
}
