import http from "node:http";
import { WebSocketServer, WebSocket } from "ws";

const port = Number(process.env.PORT || 8080);
const sessions = new Map();

const server = http.createServer((request, response) => {
  if (request.url === "/health") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(JSON.stringify({ ok: true, sessions: sessions.size }));
    return;
  }

  response.writeHead(404, { "content-type": "application/json" });
  response.end(JSON.stringify({ error: "not_found" }));
});

const wss = new WebSocketServer({ server });

function send(socket, message) {
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(message));
  }
}

function cleanup(socket) {
  const code = socket.sessionCode;
  if (!code) return;

  const session = sessions.get(code);
  if (!session) return;

  if (session.host === socket) session.host = null;
  if (session.client === socket) session.client = null;

  const peer = session.host || session.client;
  send(peer, { type: "peer_left", sessionCode: code });

  if (!session.host && !session.client) sessions.delete(code);
}

wss.on("connection", (socket) => {
  socket.isAlive = true;
  socket.on("pong", () => { socket.isAlive = true; });

  socket.on("message", (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      send(socket, { type: "error", sessionCode: "", payload: "invalid_json" });
      return;
    }

    const { type, sessionCode, payload } = message;
    if (!/^\d{6}$/.test(sessionCode || "")) {
      send(socket, { type: "error", sessionCode: sessionCode || "", payload: "invalid_session_code" });
      return;
    }

    if (type === "host_create") {
      const existing = sessions.get(sessionCode);
      if (existing?.host && existing.host !== socket) {
        send(socket, { type: "error", sessionCode, payload: "session_exists" });
        return;
      }

      const session = existing || { host: null, client: null };
      session.host = socket;
      socket.sessionCode = sessionCode;
      socket.role = "host";
      sessions.set(sessionCode, session);
      send(socket, { type: "host_ready", sessionCode });
      if (session.client) {
        send(socket, { type: "peer_joined", sessionCode });
        send(session.client, { type: "peer_joined", sessionCode });
      }
      return;
    }

    if (type === "client_join") {
      const session = sessions.get(sessionCode);
      if (!session?.host) {
        send(socket, { type: "error", sessionCode, payload: "session_not_found" });
        return;
      }
      if (session.client && session.client !== socket) {
        send(socket, { type: "error", sessionCode, payload: "session_full" });
        return;
      }

      session.client = socket;
      socket.sessionCode = sessionCode;
      socket.role = "client";
      send(socket, { type: "client_joined", sessionCode });
      send(session.host, { type: "peer_joined", sessionCode });
      send(socket, { type: "peer_joined", sessionCode });
      return;
    }

    const session = sessions.get(sessionCode);
    if (!session) {
      send(socket, { type: "error", sessionCode, payload: "session_not_found" });
      return;
    }

    const peer = socket.role === "host" ? session.client : session.host;
    if (!peer) {
      send(socket, { type: "error", sessionCode, payload: "peer_not_connected" });
      return;
    }

    send(peer, { type, sessionCode, payload });
  });

  socket.on("close", () => cleanup(socket));
  socket.on("error", () => cleanup(socket));
});

const heartbeat = setInterval(() => {
  for (const socket of wss.clients) {
    if (!socket.isAlive) {
      socket.terminate();
      continue;
    }
    socket.isAlive = false;
    socket.ping();
  }
}, 30_000);

server.on("close", () => clearInterval(heartbeat));
server.listen(port, "0.0.0.0", () => {
  console.log(`Cosyra signaling server listening on port ${port}`);
});
