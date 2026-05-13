"use client";
import { useState, useCallback } from "react";

const API_BASE = "http://localhost:8080";

const STATE_MAP: Record<string,string> = {"1":"New","2":"In Progress","3":"On Hold","6":"Resolved","7":"Closed"};
const PRI_MAP: Record<string,string> = {"1":"Critical","2":"High","3":"Medium","4":"Low"};
const PRI_CLS: Record<string,string> = {Critical:"bg-red-100 text-red-800",High:"bg-orange-100 text-orange-800",Medium:"bg-yellow-100 text-yellow-800",Low:"bg-green-100 text-green-800"};
const ST_CLS: Record<string,string> = {New:"bg-blue-100 text-blue-800","In Progress":"bg-orange-100 text-orange-800",Resolved:"bg-green-100 text-green-800",Closed:"bg-gray-100 text-gray-500"};

function disp(f: unknown): string {
  if (!f) return "—";
  if (typeof f === "string") return f || "—";
  const o = f as Record<string,unknown>;
  return String(o.display_value || o.value || "—");
}

interface Inc { id:string; num:string; desc:string; state:string; pri:string; caller:string; cat:string; date:string; }

function norm(r: Record<string,unknown>): Inc {
  const sv = disp(r.state); const pv = disp(r.priority);
  const cf = r["caller_id"] as Record<string,unknown>|string|undefined;
  const caller = cf && typeof cf==="object" ? String((cf as Record<string,unknown>).display_value||"Unknown") : typeof cf==="string"&&cf ? cf : "Unknown";
  return { id:String(r.sys_id||Math.random()), num:String(r.number||"—"), desc:String(r.short_description||"—"), state:STATE_MAP[sv]??sv, pri:PRI_MAP[pv]??pv, caller, cat:String(r.category||"—"), date:String(r.sys_created_on||"").slice(0,10)||"—" };
}

function topCaller(list: Inc[]): string {
  const c: Record<string,number> = {};
  for (const i of list) if (i.caller&&i.caller!=="Unknown") c[i.caller]=(c[i.caller]??0)+1;
  const b = Object.entries(c).sort((a,b)=>b[1]-a[1])[0];
  return b?b[0]:"";
}

type LK = "info"|"ok"|"acl"|"warn"|"err";
type LL = { msg:string; kind:LK };
const LC: Record<LK,string> = {info:"text-cyan-300",ok:"text-green-400",acl:"text-purple-400",warn:"text-yellow-400",err:"text-red-400"};

function Badge({l,c}:{l:string;c:string}) {
  return <span className={`px-2 py-0.5 rounded-full text-[11px] font-semibold ${c}`}>{l}</span>;
}

function Card({inc,own}:{inc:Inc;own?:boolean}) {
  return (
    <div className={`rounded-xl border p-3 ${own?"border-blue-300 bg-blue-50":"border-gray-200 bg-white"}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap gap-1 mb-1">
            <span className="font-mono text-[11px] text-gray-400">{inc.num}</span>
            <Badge l={inc.pri} c={PRI_CLS[inc.pri]??"bg-gray-100 text-gray-600"} />
            <Badge l={inc.state} c={ST_CLS[inc.state]??"bg-gray-100 text-gray-600"} />
          </div>
          <p className="text-sm font-semibold text-gray-800 line-clamp-2">{inc.desc}</p>
          <p className="text-xs text-gray-500 mt-0.5">👤 {inc.caller} · 🏷️ {inc.cat}</p>
        </div>
        <span className="text-[11px] text-gray-400 shrink-0">{inc.date}</span>
      </div>
    </div>
  );
}

function Term({lines}:{lines:LL[]}) {
  return (
    <div className="bg-gray-950 border border-gray-800 rounded-xl p-3 font-mono text-xs max-h-40 overflow-y-auto space-y-0.5">
      <div className="flex gap-1.5 mb-2">
        <span className="w-2.5 h-2.5 rounded-full bg-red-500 inline-block"/>
        <span className="w-2.5 h-2.5 rounded-full bg-yellow-400 inline-block"/>
        <span className="w-2.5 h-2.5 rounded-full bg-green-500 inline-block"/>
        <span className="ml-2 text-gray-600 text-[10px]">API Log — backend real</span>
      </div>
      {lines.length===0
        ? <span className="text-gray-600">// Pulsa "Consultar" para ver la llamada real</span>
        : lines.map((l,i)=><div key={i} className={LC[l.kind]}>{l.msg}</div>)
      }
    </div>
  );
}

function Panel({who}:{who:"user"|"itil"}) {
  const isItil = who==="itil";
  const hdr = isItil ? "from-purple-700 to-purple-900" : "from-blue-700 to-blue-900";
  const btn = isItil ? "bg-purple-600 hover:bg-purple-700" : "bg-blue-600 hover:bg-blue-700";
  const aclTxt = isItil ? "Sin restricción — acceso global" : "caller_id = current_user (ACL)";
  const aclCls = isItil ? "bg-purple-50 border-purple-200 text-purple-800" : "bg-blue-50 border-blue-200 text-blue-800";

  const [phase, setPhase] = useState<"idle"|"busy"|"done"|"err">("idle");
  const [logs, setLogs] = useState<LL[]>([]);
  const [items, setItems] = useState<Inc[]>([]);
  const [total, setTotal] = useState(0);
  const [demo, setDemo] = useState("");
  const [mock, setMock] = useState(false);

  const run = useCallback(async () => {
    setPhase("busy"); setLogs([]); setItems([]);
    const add = (msg:string,kind:LK) => setLogs(p=>[...p,{msg,kind}]);
    const url = `${API_BASE}/api/incidents?limit=50`;
    const t0 = Date.now();
    add(`→ GET ${url}`,"info");
    add("  Auth: OAuth 2.0 service account → ServiceNow REST API","info");
    try {
      const res = await fetch(url);
      const ms = Date.now()-t0;
      if (!res.ok) { add(`✗ HTTP ${res.status} (${ms}ms)`,"err"); add("  ¿Backend corriendo en :8080?","warn"); setPhase("err"); return; }
      const data = await res.json();
      const raw: Record<string,unknown>[] = data.result ?? data ?? [];
      const all = raw.map(norm);
      const sv: number = data.total ?? all.length;
      const isMock: boolean = data.mock_data===true;
      add(`✓ 200 OK — ${ms}ms — ${all.length} registros (SNOW total: ${sv})`,"ok");
      add(isMock ? "  ⚠ mock_data=true — datos de muestra" : "  ✓ Datos reales de ServiceNow", isMock?"warn":"ok");
      setTotal(all.length); setMock(isMock);
      add("→ ACL Engine evaluando permisos...","acl");
      if (isItil) {
        add("  Rol [itil] → acceso global habilitado","acl");
        add(`  ✓ ${all.length} tickets visibles`,"ok");
        setItems(all); setDemo("");
      } else {
        const u = topCaller(all);
        const filtered = u ? all.filter(i=>i.caller===u) : all.slice(0,1);
        add("  Rol [user] → restricción caller_id","acl");
        add(u ? `  caller_id = "${u}"` : "  Sin caller dominante","acl");
        add(`  ✓ ${filtered.length}/${all.length} visibles  ⚠ filtro demo client-side`,"ok");
        setItems(filtered); setDemo(u);
      }
      setPhase("done");
    } catch(e) {
      const ms = Date.now()-t0;
      add(`✗ Error de red (${ms}ms): ${e instanceof Error?e.message:String(e)}`,"err");
      add("  ¿Está el backend Spring Boot corriendo en localhost:8080?","warn");
      setPhase("err");
    }
  },[isItil]);

  const reset = () => { setPhase("idle"); setLogs([]); setItems([]); setDemo(""); setTotal(0); };
  const openN = items.filter(i=>i.state==="New"||i.state==="In Progress").length;
  const critN = items.filter(i=>i.pri==="Critical").length;

  return (
    <div className="flex flex-col rounded-2xl overflow-hidden border border-gray-200 shadow-lg bg-white">
      <div className={`bg-gradient-to-r ${hdr} p-5 text-white`}>
        <div className="flex items-center gap-3 mb-3">
          <div className={`w-12 h-12 rounded-full flex items-center justify-center text-lg font-bold shadow-md ${isItil?"bg-purple-500":"bg-blue-500"}`}>
            {isItil?"ML":"EU"}
          </div>
          <div>
            <p className="font-bold text-base">{isItil?"María López":"Usuario End-User"}{!isItil&&demo?` · ${demo}`:""}</p>
            <p className="text-white/70 text-xs">{isItil?"IT Support · analyst":"Negocio · user"}</p>
          </div>
          <span className="ml-auto bg-white/20 px-2 py-0.5 rounded-full text-xs font-mono">
            {isItil?"itil":"end-user"}
          </span>
        </div>
        <div className={`rounded-lg border px-3 py-2 text-xs ${aclCls}`}>
          <span className="font-bold">ACL: </span>
          <code className="ml-1">{aclTxt}</code>
        </div>
      </div>

      <div className="p-4 flex flex-col gap-4">
        <div className="flex gap-2">
          <button onClick={run} disabled={phase==="busy"}
            className={`flex-1 ${btn} text-white text-sm font-semibold py-2.5 rounded-xl transition-all disabled:opacity-50`}>
            {phase==="busy" ? "Consultando..." : "🔍 Consultar Tickets"}
          </button>
          {phase!=="idle" && (
            <button onClick={reset} className="px-4 py-2.5 rounded-xl border border-gray-300 text-gray-600 text-sm hover:bg-gray-50">
              Reset
            </button>
          )}
        </div>

        {phase==="done" && (
          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="rounded-xl bg-gray-50 border p-2">
              <p className="text-2xl font-bold text-gray-800">{items.length}</p>
              <p className="text-[11px] text-gray-500">visibles</p>
            </div>
            <div className="rounded-xl bg-orange-50 border border-orange-200 p-2">
              <p className="text-2xl font-bold text-orange-700">{openN}</p>
              <p className="text-[11px] text-orange-500">abiertos</p>
            </div>
            <div className="rounded-xl bg-red-50 border border-red-200 p-2">
              <p className="text-2xl font-bold text-red-700">{critN}</p>
              <p className="text-[11px] text-red-500">críticos</p>
            </div>
          </div>
        )}

        {mock && phase==="done" && (
          <div className="rounded-lg bg-yellow-50 border border-yellow-300 px-3 py-2 text-xs text-yellow-800">
            ⚠ Datos de muestra (mock). ServiceNow no disponible.
          </div>
        )}

        {!isItil && phase==="done" && (
          <div className="rounded-lg bg-blue-50 border border-blue-200 px-3 py-2 text-xs text-blue-800">
            <strong>Demo ACL:</strong> mostrando solo tickets de <em>{demo||"usuario desconocido"}</em>.
            En producción ServiceNow aplica esta restricción en servidor mediante <code>caller_id=javascript:gs.getUserID()</code>.
          </div>
        )}

        <Term lines={logs} />

        {phase==="done" && items.length>0 && (
          <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
            {items.map(inc=><Card key={inc.id} inc={inc} own={!isItil} />)}
          </div>
        )}

        {phase==="done" && items.length===0 && (
          <div className="text-center py-8 text-gray-400 text-sm">Sin tickets visibles para este usuario.</div>
        )}

        {phase==="err" && (
          <div className="rounded-xl bg-red-50 border border-red-200 p-4 text-sm text-red-700 text-center">
            ❌ No se pudo conectar al backend.<br/>
            <span className="text-xs text-red-500">Inicia el backend Spring Boot en puerto 8080.</span>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AclDemoPage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-100 to-blue-50 py-10 px-4">
      <div className="max-w-5xl mx-auto">
        <div className="text-center mb-8">
          <span className="inline-block bg-white border border-gray-200 rounded-full px-4 py-1 text-xs font-semibold text-gray-500 mb-3">
            Demo interactivo — llamadas reales al backend
          </span>
          <h1 className="text-3xl font-extrabold text-gray-900 mb-2">
            ServiceNow ACL Demo
          </h1>
          <p className="text-gray-500 text-sm max-w-xl mx-auto">
            Dos roles, mismo endpoint <code className="bg-gray-100 px-1 rounded">/api/incidents</code>, distinta visibilidad.
            Los datos vienen del backend Spring Boot conectado a ServiceNow vía OAuth 2.0.
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          <Panel who="itil" />
          <Panel who="user" />
        </div>

        <div className="mt-6 rounded-2xl border border-gray-200 bg-white p-5 text-sm text-gray-600 space-y-2">
          <p className="font-bold text-gray-800">¿Cómo funciona?</p>
          <ul className="list-disc pl-5 space-y-1">
            <li>Ambos paneles llaman a <code className="bg-gray-100 px-1 rounded">GET /api/incidents?limit=50</code> en el mismo backend.</li>
            <li>El backend autentica con ServiceNow usando OAuth 2.0 (service account).</li>
            <li><strong>ITIL analyst</strong>: ve todos los incidents sin restricción de ACL.</li>
            <li><strong>End-user</strong>: en producción ServiceNow restringe por <code className="bg-gray-100 px-1 rounded">caller_id=javascript:gs.getUserID()</code>. Esta demo simula ese filtro client-side sobre los datos reales.</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
