# Inbound-only TCP forwarder LISTEN_PORT -> TARGET_HOST:TARGET_PORT: the browser's only way into one env copy's
# internal network (env/README.md "Isolation"). It accepts connections; it never opens any toward the outside.
import asyncio, os

TH, TP, LP = os.environ["TARGET_HOST"], int(os.environ["TARGET_PORT"]), int(os.environ["LISTEN_PORT"])


async def pipe(r, w):
    try:
        while True:
            b = await r.read(65536)
            if not b:
                break
            w.write(b)
            await w.drain()
    except Exception:
        pass
    finally:
        try:
            w.close()
        except Exception:
            pass


async def handle(cr, cw):
    try:
        ur, uw = await asyncio.open_connection(TH, TP)
    except Exception:
        cw.close()
        return
    await asyncio.gather(pipe(cr, uw), pipe(ur, cw))


async def main():
    srv = await asyncio.start_server(handle, "0.0.0.0", LP)
    print("tcpproxy ready", flush=True)
    async with srv:
        await srv.serve_forever()


asyncio.run(main())
