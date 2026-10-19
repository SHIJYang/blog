import json, os, re, ssl, sys, urllib.request, urllib.error

MD = r"D:\sjy\doit\flutter_application_1\_task_spec.md"
CA = os.path.join(os.environ["DSH_HOME"], "baota_root_ca.crt")

raw = open(MD, encoding="utf-8").read()
m = re.search(r"```json\s*(\{.*?\})\s*```", raw, re.S)
info = json.loads(m.group(1))
url = info["public_host"]
token = info["api_token"]

def rpc(ssl_ctx, payload, session=None, extra=None):
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
        "Authorization": "Bearer " + token,
    }
    if session:
        headers["Mcp-Session-Id"] = session
    if extra:
        headers.update(extra)
    data = json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, context=ssl_ctx, timeout=30) as r:
            body = r.read().decode("utf-8", "replace")
            return r.status, dict(r.headers), body
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")

def parse(body):
    out = []
    for line in body.splitlines():
        line = line.strip()
        if line.startswith("data:"):
            out.append(line[5:].strip())
        elif line.startswith("{"):
            out.append(line)
    res = None
    for chunk in out:
        try:
            res = json.loads(chunk)
        except Exception:
            pass
    return res

print("== 1. 未加证书（默认信任库）==")
try:
    st, hd, body = rpc(ssl.create_default_context(), {"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "dsh-verify", "version": "1.0"}}})
    print("status", st)
except Exception as e:
    print("FAIL as expected:", type(e).__name__, str(e)[:160])

print("== 2. 加 baota_root_ca.crt ==")
ctx = ssl.create_default_context(cafile=CA)
init = {"jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                   "clientInfo": {"name": "dsh-verify", "version": "1.0"}}}
st, hd, body = rpc(ctx, init)
print("initialize status:", st)
sid = hd.get("Mcp-Session-Id") or hd.get("mcp-session-id")
print("session id present:", bool(sid))
r = parse(body)
if r and "result" in r:
    ri = r["result"]
    print("serverInfo:", json.dumps(ri.get("serverInfo", {}), ensure_ascii=False))
    print("protocolVersion:", ri.get("protocolVersion"))
    print("capabilities:", json.dumps(ri.get("capabilities", {}), ensure_ascii=False))
else:
    print("body:", body[:600])

print("== 3. notifications/initialized ==")
st2, _, b2 = rpc(ctx, {"jsonrpc": "2.0", "method": "notifications/initialized"}, session=sid)
print("status:", st2)

print("== 4. tools/list ==")
st3, _, b3 = rpc(ctx, {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}, session=sid)
print("status:", st3)
r3 = parse(b3)
if r3 and "result" in r3:
    tools = r3["result"].get("tools", [])
    print("tool count:", len(tools))
    for t in tools:
        print("  -", t.get("name"), "|", (t.get("description") or "")[:70].replace("\n", " "))
else:
    print("body:", b3[:800])
