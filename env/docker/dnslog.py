# Logging DNS for one env copy's internal network (env/README.md "Isolation").
# Answers only the names in ALLOW (comma-separated), by asking Docker's embedded DNS (127.0.0.11) on the same
# network; every other name gets NXDOMAIN and is never forwarded. Logs every query: time, client, name, type, result.
import os, socket, struct, time

allow = {n.strip().lower() for n in os.environ["ALLOW"].split(",") if n.strip()}
log = open(os.environ.get("LOG", "/log/dns.log"), "a", buffering=1)
TYPES = {1: "A", 28: "AAAA", 5: "CNAME", 12: "PTR", 15: "MX", 16: "TXT", 33: "SRV", 65: "HTTPS"}
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("0.0.0.0", 53))
up = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
up.settimeout(2)
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
                up.sendto(data, ("127.0.0.11", 53))
                reply, _ = up.recvfrom(4096)
                res = "forwarded"
            except Exception as e:
                res = "SERVFAIL %s" % type(e).__name__
                reply = struct.pack(">HHHHHH", tid, 0x8402 | rd, 1, 0, 0, 0) + data[12:i + 5]
        s.sendto(reply, addr)
        log.write("%s %s %s %s %s\n" % (now, addr[0], qname, TYPES.get(qtype, qtype), res))
    except Exception as e:
        log.write("%s %s PARSE-ERROR %r\n" % (now, addr[0], e))
