# Logging DNS for one env copy's internal network (env/README.md "Isolation").
# Answers only the names in ALLOW (comma-separated), by asking Docker's embedded DNS (127.0.0.11) on the same
# network; every other name gets NXDOMAIN and is never forwarded. Logs every query: time, client, name, type, result.
# Each forwarded query gets its own upstream socket and only a reply with its transaction id counts, so a late reply
# to an earlier query can never be handed to a later one. UPSTREAM (host:port) is for tests only.
import os, socket, struct, time

allow = {n.strip().lower() for n in os.environ["ALLOW"].split(",") if n.strip()}
log = open(os.environ.get("LOG", "/log/dns.log"), "a", buffering=1)
TYPES = {1: "A", 28: "AAAA", 5: "CNAME", 12: "PTR", 15: "MX", 16: "TXT", 33: "SRV", 65: "HTTPS"}
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("0.0.0.0", 53))
UP_HOST, UP_PORT = os.environ.get("UPSTREAM", "127.0.0.11:53").rsplit(":", 1)


def forward(query, timeout=2.0):
    """Ask the upstream on a fresh socket; return its reply to this query (same transaction id), or raise."""
    deadline = time.monotonic() + timeout
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as up:
        up.connect((UP_HOST, int(UP_PORT)))  # only datagrams from the upstream reach this socket
        up.send(query)
        while True:
            left = deadline - time.monotonic()
            if left <= 0:
                raise socket.timeout("no reply with id %d" % struct.unpack(">H", query[:2])[0])
            up.settimeout(left)
            reply = up.recv(4096)
            if reply[:2] == query[:2]:
                return reply


print("dnslog ready", flush=True)
while True:
    data, addr = s.recvfrom(4096)
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    try:
        tid, flags = struct.unpack(">HH", data[:4])
        i, labels = 12, []
        while data[i]:
            n = data[i]
            labels.append(data[i + 1:i + 1 + n].decode("ascii", "replace"))
            i += n + 1
        qtype, = struct.unpack(">H", data[i + 1:i + 3])
        qname = ".".join(labels).lower()
        rd = flags & 0x0100
        res = "NXDOMAIN"
        reply = struct.pack(">HHHHHH", tid, 0x8403 | rd, 1, 0, 0, 0) + data[12:i + 5]
        if qname in allow:
            try:
                reply = forward(data)
                res = "forwarded"
            except Exception as e:
                res = "SERVFAIL %s" % type(e).__name__
                reply = struct.pack(">HHHHHH", tid, 0x8402 | rd, 1, 0, 0, 0) + data[12:i + 5]
        s.sendto(reply, addr)
        log.write("%s %s %s %s %s\n" % (now, addr[0], qname, TYPES.get(qtype, qtype), res))
    except Exception as e:
        log.write("%s %s PARSE-ERROR %r\n" % (now, addr[0], e))
