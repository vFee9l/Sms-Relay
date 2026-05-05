import { Router, type IRouter } from "express";

const router: IRouter = Router();

interface SmsPayload {
  id: string;
  receivedAt: string;
  secret: string;
  from: string;
  message: string;
  sent_timestamp: number;
  sent_to: string;
  message_id: string;
  device_id: string;
}

// In-memory store — demo only, resets on server restart
const messages: SmsPayload[] = [];
const MAX_MESSAGES = 200;

// POST /api/webhook — receives SMS payload from the Android app
router.post("/webhook", (req, res) => {
  const body = req.body;

  const entry: SmsPayload = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    receivedAt: new Date().toISOString(),
    secret:          String(body.secret          ?? ""),
    from:            String(body.from            ?? ""),
    message:         String(body.message         ?? ""),
    sent_timestamp:  Number(body.sent_timestamp  ?? Date.now()),
    sent_to:         String(body.sent_to         ?? ""),
    message_id:      String(body.message_id      ?? ""),
    device_id:       String(body.device_id       ?? ""),
  };

  messages.unshift(entry);
  if (messages.length > MAX_MESSAGES) messages.length = MAX_MESSAGES;

  req.log.info({ from: entry.from, sent_to: entry.sent_to }, "Webhook received");
  res.status(200).json({ ok: true, id: entry.id });
});

// GET /api/webhook/messages — poll for stored messages
router.get("/webhook/messages", (_req, res) => {
  res.json({ messages, total: messages.length });
});

// DELETE /api/webhook/messages — clear all stored messages
router.delete("/webhook/messages", (_req, res) => {
  messages.length = 0;
  res.json({ ok: true });
});

export default router;
