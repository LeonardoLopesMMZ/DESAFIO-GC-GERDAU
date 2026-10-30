const FILA = "mokka-fila";
const CATALOGO = "mokka-catalogo";
let semConexao = false;
let enviando = false;

const normalizar = t => (t || "").normalize("NFD").replace(/\p{M}/gu, "").toUpperCase().replace(/[^A-Z0-9%]+/g, " ").trim();
const palavrasDe = t => new Set(normalizar(t).split(" ").filter(w => w && w !== "SV"));
const fila = () => guardado.ler(FILA) || [];
const catalogoLocal = () => guardado.ler(CATALOGO) || [];

function avisarModoLocal() {
  const n = fila().length, b = $("modo-local");
  b.classList.toggle("oculto", !semConexao && !n);
  b.textContent = semConexao
    ? `Sem conexão com o servidor: o MOKKA continua funcionando neste computador${n ? ` · ${n} pedido(s) guardado(s) para enviar` : ""}.`
    : `Servidor de volta: enviando ${n} pedido(s) guardado(s)…`;
}

function ficarSemConexao(sem) {
  if (semConexao === sem) return;
  semConexao = sem;
  document.body.classList.toggle("sem-conexao", sem);
  avisarModoLocal();
}

function parecido(a, b) {
  if (!a.size || !b.size) return 0;
  let comum = 0;
  a.forEach(w => { if (b.has(w)) comum++; });
  return comum / (a.size + b.size - comum);
}

function consultaLocal(p) {
  const pedido = palavrasDe(p.descricao), un = (p.unidade || "").toUpperCase(), lista = catalogoLocal();
  const notas = [];
  for (const [codigo, descricao, unidade] of lista) {
    const n = parecido(pedido, palavrasDe(descricao)) * (unidade === un ? 1 : 0.85);
    if (n > 0) notas.push({ codigo, descricao, unidade, n });
  }
  notas.sort((a, b) => b.n - a.n);
  const candidatos = notas.slice(0, 10).map(c => {
    const outras = palavrasDe(c.descricao);
    const diferencas = [...pedido].filter(w => !outras.has(w)).concat([...outras].filter(w => !pedido.has(w))).map(w => w.toLowerCase());
    if (c.unidade !== un) diferencas.push(`UNIDADE ${un}/${c.unidade}`);
    return { codigo: c.codigo, descricao: c.descricao, unidade: c.unidade, aderencia: Math.round(c.n * 100),
      classe: c.n > 0.999 ? "DUPLICADO" : c.n >= 0.6 ? "REVISAR" : "DIFERENTE", diferencas };
  });
  const top = candidatos[0];
  const resultado = top && top.classe === "DUPLICADO" ? "BLOQUEAR" : top && top.classe === "REVISAR" ? "REVISAR" : "LIBERAR";
  const motivo = { BLOQUEAR: `Já existe: ${top && top.codigo} ${top && top.descricao}`, REVISAR: `Parecido com ${top && top.codigo} ${top && top.descricao}`, LIBERAR: "Nenhum código igual" }[resultado];
  return { solicitacao_id: "local", resultado, candidatos, sugestao: null, local: true,
    motivo: motivo + " · modo local, sem o servidor" + (lista.length ? "" : " (o catálogo ainda não foi guardado neste computador)") };
}

function guardarDecisao(corpo) {
  const f = fila();
  f.push({ pedido: atual.pedido, acao: corpo.acao, codigo: corpo.codigo || null, justificativa: corpo.justificativa || "",
    quem: sessao ? sessao.nome : "", quando: Date.now() });
  guardado.gravar(FILA, f);
  avisarModoLocal();
  return { status: "GUARDADO_LOCAL", codigo_escolhido: corpo.codigo || null };
}

function buscarLocal(busca) {
  const b = normalizar(busca);
  return catalogoLocal().filter(([c, d]) => c.startsWith(b) || normalizar(d).includes(b)).slice(0, 20)
    .map(([codigo, descricao, unidade]) => ({ codigo, descricao, unidade, grupo: "", status: "ATIVO", motivo: "" }));
}

function modoLocal(url, corpo) {
  ficarSemConexao(true);
  if (url === "/api/consultas" && corpo) return consultaLocal(corpo);
  if (/^\/api\/solicitacoes\/[^/]+\/decisao$/.test(url) && corpo && atual && atual.pedido) return guardarDecisao(corpo);
  if (url === "/api/consultas/palavras") return { palavras: [] };
  if (url === "/api/acesso/entrar" && corpo) return { token: null, nome: corpo.nome, metodo: "NOME", local: true };
  if (url === "/api/unidades") return guardado.ler("mokka-unidades") || [];
  if (url.startsWith("/api/codigos?busca=")) return buscarLocal(decodeURIComponent(url.slice(19)));
  if (url === "/api/codigos/resumo") {
    const n = catalogoLocal().length;
    return { na_base: n, oficiais: n, repetidos: 0, criados: 0, consultas: 0, duplicados_evitados: 0 };
  }
  if (url === "/api/codigos/novos" || url === "/api/codigos/padronizados") return [];
  throw new Error("Sem conexão com o servidor. Consultar e pedir código continuam funcionando; o resto volta quando o MOKKA religar.");
}

async function enviarFila() {
  if (enviando || !fila().length) return avisarModoLocal();
  if (!sessao || !sessao.token) { pedirEntrada().catch(() => {}); return; }
  enviando = true;
  avisarModoLocal();
  let ok = 0;
  while (fila().length) {
    const item = fila()[0];
    try {
      const r = await chamar("/api/consultas", item.pedido, { semLocal: true });
      await chamar(`/api/solicitacoes/${r.solicitacao_id}/decisao`, { acao: item.acao, codigo: item.codigo,
        justificativa: item.justificativa || "Guardado no modo local" + (item.quem ? " por " + item.quem : "") }, { semLocal: true });
      ok++;
    } catch (e) {
      if (e.rede || e.entrar) break;
      aviso(`Pedido guardado "${M(item.pedido.descricao)}" não entrou: ${e.message}`, "erro");
    }
    guardado.gravar(FILA, fila().slice(1));
  }
  enviando = false;
  avisarModoLocal();
  if (ok) {
    aviso(`${ok} pedido(s) guardado(s) neste computador foram enviados ao sistema.`, "LIBERAR");
    historico();
    pendentes();
  }
}

async function guardarCatalogo() {
  try {
    if (!sessao || !sessao.token) return;
    const r = await fetch("/api/codigos/leve", { headers: { Authorization: "Bearer " + sessao.token } });
    if (r.ok) guardado.gravar(CATALOGO, await r.json());
  } catch (e) {}
}

async function vigiar() {
  if (!semConexao && !fila().length) return;
  try {
    const r = await fetch("/api/sistema", { cache: "no-store" });
    if (!r.ok) throw new Error();
  } catch (e) { ficarSemConexao(true); return; }
  if (semConexao) {
    ficarSemConexao(false);
    guardarCatalogo();
    carregarUnidades();
  }
  enviarFila();
}

function prepararModoLocal() {
  if ("serviceWorker" in navigator && window.isSecureContext) {
    addEventListener("load", () => {
      const arquivos = ["/", ...[...document.querySelectorAll("script[src], link[rel=stylesheet]")].map(e => e.getAttribute("src") || e.getAttribute("href"))];
      navigator.serviceWorker.register("sw.js").then(() => caches.open("mokka")).then(c => c.addAll(arquivos)).catch(() => {});
    }, { once: true });
  }
  setInterval(vigiar, 5000);
  avisarModoLocal();
}
