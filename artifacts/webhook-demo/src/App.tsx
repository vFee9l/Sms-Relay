import { useState, useEffect, useCallback, useRef } from "react";

interface SmsMessage {
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

const BASE = import.meta.env.BASE_URL.replace(/\/$/, "");

function formatTs(ts: number) {
  return new Date(ts).toLocaleString();
}

function formatReceived(iso: string) {
  return new Date(iso).toLocaleTimeString();
}

function simLabel(sentTo: string) {
  if (!sentTo || sentTo === "SIM1") return { label: "SIM 1", color: "bg-blue-600" };
  if (sentTo === "SIM2") return { label: "SIM 2", color: "bg-teal-600" };
  return { label: sentTo, color: "bg-slate-500" };
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };
  return (
    <button
      onClick={copy}
      className="ml-2 px-3 py-1 text-xs rounded bg-blue-600 hover:bg-blue-700 text-white font-medium transition-colors"
    >
      {copied ? "Copied!" : "Copy"}
    </button>
  );
}

function MessageCard({ msg }: { msg: SmsMessage }) {
  const sim = simLabel(msg.sent_to);
  const isTest = msg.from.startsWith("TEST_");

  return (
    <div className={`rounded-xl border bg-white shadow-sm p-4 ${isTest ? "border-amber-300 bg-amber-50/40" : "border-slate-200"}`}>
      {/* Header row */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`text-xs font-bold text-white px-2 py-0.5 rounded ${sim.color}`}>
            {sim.label}
          </span>
          <span className="font-semibold text-slate-800 text-sm">{msg.from}</span>
          {isTest && (
            <span className="text-xs bg-amber-200 text-amber-800 px-2 py-0.5 rounded font-medium">
              TEST
            </span>
          )}
        </div>
        <span className="text-xs text-slate-400 whitespace-nowrap shrink-0">
          received {formatReceived(msg.receivedAt)}
        </span>
      </div>

      {/* Message body */}
      <p className="text-slate-900 text-sm mb-3 leading-relaxed break-words whitespace-pre-wrap rounded bg-slate-50 border border-slate-100 px-3 py-2">
        {msg.message}
      </p>

      {/* Payload fields */}
      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-500 font-mono">
        <div><span className="text-slate-400">secret</span> <span className="text-slate-700">{msg.secret || "—"}</span></div>
        <div><span className="text-slate-400">device_id</span> <span className="text-slate-700">{msg.device_id || "—"}</span></div>
        <div><span className="text-slate-400">sent_to</span> <span className="text-slate-700">{msg.sent_to || "—"}</span></div>
        <div><span className="text-slate-400">message_id</span> <span className="text-slate-700">{msg.message_id || "—"}</span></div>
        <div className="col-span-2"><span className="text-slate-400">sent_timestamp</span> <span className="text-slate-700">{formatTs(msg.sent_timestamp)}</span></div>
      </div>
    </div>
  );
}

export default function App() {
  const [messages, setMessages] = useState<SmsMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [lastPoll, setLastPoll] = useState<Date | null>(null);
  const [clearing, setClearing] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const webhookUrl = (() => {
    const { protocol, host } = window.location;
    return `${protocol}//${host}/api/webhook`;
  })();

  const fetchMessages = useCallback(async () => {
    try {
      const res = await fetch(`${BASE}/api/webhook/messages`);
      if (!res.ok) return;
      const data = await res.json();
      setMessages(data.messages ?? []);
      setLastPoll(new Date());
    } catch {
      // silent — network may be briefly unavailable
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMessages();
    intervalRef.current = setInterval(fetchMessages, 2500);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [fetchMessages]);

  const clearMessages = async () => {
    setClearing(true);
    try {
      await fetch(`${BASE}/api/webhook/messages`, { method: "DELETE" });
      setMessages([]);
    } finally {
      setClearing(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Top bar */}
      <header className="bg-white border-b border-slate-200 shadow-sm sticky top-0 z-10">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center">
              <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
            </div>
            <div>
              <h1 className="font-bold text-slate-900 text-base leading-none">SMS Webhook Demo</h1>
              <p className="text-xs text-slate-500 mt-0.5">Live SMS receiver</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {lastPoll && (
              <span className="text-xs text-slate-400 hidden sm:block">
                Updated {lastPoll.toLocaleTimeString()}
              </span>
            )}
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
              <span className="text-xs text-green-700 font-medium">Live</span>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-6 space-y-5">
        {/* Webhook URL card */}
        <div className="rounded-xl border border-blue-200 bg-blue-50 p-4">
          <p className="text-xs font-semibold text-blue-700 uppercase tracking-wide mb-2">
            Your Webhook URL
          </p>
          <p className="text-xs text-blue-600 mb-1">
            Paste this into the SMS Forwarder app as the Webhook URL for each SIM.
          </p>
          <div className="flex items-center gap-2 mt-2">
            <code className="text-sm font-mono bg-white border border-blue-200 text-blue-900 px-3 py-2 rounded-lg flex-1 break-all">
              {webhookUrl}
            </code>
            <CopyButton text={webhookUrl} />
          </div>
        </div>

        {/* Stats + controls row */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-slate-700">
              {messages.length} message{messages.length !== 1 ? "s" : ""}
            </span>
            {messages.length > 0 && (
              <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full font-medium">
                {messages.filter(m => !m.from.startsWith("TEST_")).length} real
                {" · "}
                {messages.filter(m => m.from.startsWith("TEST_")).length} test
              </span>
            )}
          </div>
          {messages.length > 0 && (
            <button
              onClick={clearMessages}
              disabled={clearing}
              className="text-xs text-red-500 hover:text-red-700 border border-red-200 hover:border-red-400 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
            >
              {clearing ? "Clearing…" : "Clear all"}
            </button>
          )}
        </div>

        {/* Message list */}
        {loading ? (
          <div className="text-center py-16 text-slate-400 text-sm">Loading…</div>
        ) : messages.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center">
            <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center mx-auto mb-3">
              <svg className="w-6 h-6 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2v10z" />
              </svg>
            </div>
            <p className="text-slate-500 font-medium text-sm">Waiting for messages…</p>
            <p className="text-slate-400 text-xs mt-1">
              Send a test from the SMS Forwarder app, or wait for a real SMS.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {messages.map(msg => (
              <MessageCard key={msg.id} msg={msg} />
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
