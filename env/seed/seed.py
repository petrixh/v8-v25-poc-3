# Synthetic seed for one env copy (env/README.md "Seed"), created through the app's own Management API on
# mgmt-server. Fixed names and values; the only generated values are the ids and timestamps the app assigns.
# Run by env/seed/seed.sh in a python container on the copy's internal network, once, on a fresh database.
#   python seed.py <mgmt base url>      credentials in env MGMT_USER / MGMT_PASSWORD (never printed)
import base64, json, os, sys, urllib.error, urllib.request

BASE = sys.argv[1].rstrip("/") + "/rest/v1"
AUTH = "Basic " + base64.b64encode(("%s:%s" % (os.environ["MGMT_USER"], os.environ["MGMT_PASSWORD"])).encode()).decode()

# ---- the seed: change it here, and in README.md "Seed"
TARGET_TAGS = [("Lab", "Seed: devices on the test bench", "#2e7d32"),
               ("Field", "Seed: devices in the field", "#1565c0")]
TARGETS = [("seed-dev-%02d" % i, "Seed Device %02d" % i, "Seed target %02d" % i) for i in range(1, 11)]
TAGGED = {"Lab": ["seed-dev-01", "seed-dev-02", "seed-dev-03", "seed-dev-04"],
          "Field": ["seed-dev-05", "seed-dev-06", "seed-dev-07", "seed-dev-08"]}  # 09 and 10 untagged
# software modules: (key, name, version, type key, vendor); types "os" and "application" are the app's defaults
MODULES = [("os1", "Seed OS", "1.0.0", "os", "Seed Vendor"),
           ("os2", "Seed OS", "2.0.0", "os", "Seed Vendor"),
           ("app1", "Seed App", "1.0.0", "application", "Seed Vendor"),
           ("app11", "Seed App", "1.1.0", "application", "Seed Vendor"),
           ("agent1", "Seed Agent", "1.0.0", "application", "Seed Vendor")]
# distribution sets: (key, name, version, type key, module keys); types "os", "app", "os_app" are the app's defaults
DISTRIBUTIONS = [("osbase", "Seed OS Base", "1.0.0", "os", ["os1"]),
                 ("bundle1", "Seed Bundle", "1.0.0", "os_app", ["os1", "app1"]),
                 ("bundle2", "Seed Bundle", "2.0.0", "os_app", ["os2", "app11", "agent1"]),
                 ("apps", "Seed Apps", "1.0.0", "app", ["app11"])]
DS_TAGS = [("Released", "Seed: released distributions", "#6a1b9a", ["osbase", "bundle1"])]
TARGET_FILTERS = [("Seed Lab devices", "tag==Lab")]


def call(method, path, body=None):
    req = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Authorization": AUTH, "Accept": "application/json",
                                          "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        sys.exit("seed: %s %s -> HTTP %s %s" % (method, path, e.code, e.read()[:500].decode("utf-8", "replace")))


def total(path):
    return call("GET", path + "?limit=1")["total"]


# a fresh database only: the seed is not additive
for p in ("/targets", "/softwaremodules", "/distributionsets", "/targettags", "/distributionsettags", "/targetfilters"):
    if total(p):
        sys.exit("seed: %s is not empty; the seed runs only on a fresh database" % p)

# One entity per request throughout: a bulk create stores its items in no fixed order, so ids would vary per run.
tag_ids = {}
for n, d, c in TARGET_TAGS:
    tag_ids[n] = call("POST", "/targettags", [{"name": n, "description": d, "colour": c}])[0]["id"]
for c, n, d in TARGETS:
    call("POST", "/targets", [{"controllerId": c, "name": n, "description": d}])
for tag, ctrl in TAGGED.items():
    call("POST", "/targettags/%d/assigned" % tag_ids[tag], [{"controllerId": c} for c in ctrl])

mod_ids = {}
for key, name, version, typ, vendor in MODULES:
    mod_ids[key] = call("POST", "/softwaremodules", [{"name": name, "version": version, "type": typ, "vendor": vendor,
                                                      "description": "Seed module %s %s" % (name, version)}])[0]["id"]
ds_ids = {}
for key, name, version, typ, mods in DISTRIBUTIONS:
    ds_ids[key] = call("POST", "/distributionsets", [{"name": name, "version": version, "type": typ,
                                                      "description": "Seed distribution %s %s" % (name, version),
                                                      "modules": [{"id": mod_ids[m]} for m in mods]}])[0]["id"]
for name, desc, colour, sets in DS_TAGS:
    tid = call("POST", "/distributionsettags", [{"name": name, "description": desc, "colour": colour}])[0]["id"]
    call("POST", "/distributionsettags/%d/assigned" % tid, [{"id": ds_ids[s]} for s in sets])
for name, query in TARGET_FILTERS:
    call("POST", "/targetfilters", {"name": name, "query": query})

counts = {p: total("/" + p) for p in ("targets", "targettags", "softwaremodules", "distributionsets",
                                       "distributionsettags", "targetfilters")}
print("seed done: " + ", ".join("%s %d" % kv for kv in counts.items()))
expect = {"targets": 10, "targettags": 2, "softwaremodules": 5, "distributionsets": 4, "distributionsettags": 1,
          "targetfilters": 1}
if counts != expect:
    sys.exit("seed: counts %s, expected %s" % (counts, expect))
