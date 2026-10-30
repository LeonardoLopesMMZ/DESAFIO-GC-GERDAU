const $ = id => document.getElementById(id);
const M = t => (t ?? "").toString().toUpperCase();
const SEM_ACENTO = t => t.normalize("NFD").replace(/\p{M}/gu, "");
let atual = null;
let sessao = null;

const guardado = {
  ler(k) { try { return JSON.parse(localStorage.getItem(k)); } catch (e) { return null; } },
  gravar(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} },
  sessao(v) {
    try { v === undefined ? null : v ? sessionStorage.setItem("mokka-sessao", JSON.stringify(v)) : sessionStorage.removeItem("mokka-sessao"); } catch (e) {}
    try { return JSON.parse(sessionStorage.getItem("mokka-sessao")); } catch (e) { return null; }
  }
};

function celula(tr, valor, classe) {
  const td = document.createElement("td");
  if (valor instanceof Node) td.appendChild(valor); else td.textContent = valor ?? "";
  if (classe) td.className = classe;
  if (valor === null || valor === undefined || valor === "") td.dataset.vazio = "";
  tr.appendChild(td);
  return td;
}

function rotular(corpo) {
  const nomes = [...corpo.closest("table").querySelectorAll("thead th")].map(th => th.textContent.trim());
  [...corpo.rows].forEach((tr, i) => {
    tr.classList.add("nova");
    tr.style.setProperty("--i", Math.min(i, 15));
    [...tr.cells].forEach((td, j) => {
      td.dataset.r = nomes[j] || "";
      if (!nomes[j]) td.classList.add("acao");
    });
  });
}

function botaoCodigo(codigo) {
  if (!codigo) return "";
  const b = document.createElement("button");
  b.type = "button";
  b.className = "cod";
  b.textContent = codigo;
  b.title = "Ver a ficha do código";
  b.onclick = e => { e.stopPropagation(); ficha(codigo); };
  return b;
}

function aviso(texto, classe) {
  const a = $("aviso");
  a.textContent = texto;
  a.className = (classe || "") + " mostra";
  a.style.animation = "none";
  void a.offsetWidth;
  a.style.animation = "";
}

function esconderAviso() { $("aviso").className = ""; }

function contar(el, alvo) {
  const ini = performance.now(), dur = 700;
  const passo = t => {
    const p = Math.min(1, (t - ini) / dur);
    el.textContent = Math.round(alvo * (1 - Math.pow(1 - p, 3))).toLocaleString("pt-BR");
    if (p < 1) requestAnimationFrame(passo);
  };
  requestAnimationFrame(passo);
}

const quando = t => t ? new Date(t).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }) : "";

let pendentesRede = 0;
function rede(delta) {
  pendentesRede = Math.max(0, pendentesRede + delta);
  document.body.classList.toggle("carregando", pendentesRede > 0);
}

async function chamar(url, corpo, opcoes = {}) {
  const cabecalho = {};
  if (corpo) cabecalho["Content-Type"] = "application/json";
  if (sessao && !opcoes.semToken) cabecalho.Authorization = "Bearer " + sessao.token;
  rede(1);
  let r;
  try { r = await fetch(url, corpo ? { method: "POST", headers: cabecalho, body: JSON.stringify(corpo) } : { headers: cabecalho }); }
  catch (e) {
    if (opcoes.semLocal) throw Object.assign(new Error("Sem conexão com o servidor."), { rede: true });
    return modoLocal(url, corpo);
  }
  finally { rede(-1); }
  if (r.status >= 500 && !opcoes.semLocal && /^\/api\/(consultas|solicitacoes\/[^/]+\/decisao)/.test(url)) return modoLocal(url, corpo);
  if (semConexao && r.ok) ficarSemConexao(false);
  const dados = await r.json().catch(() => ({}));
  if (r.status === 401 && !opcoes.semToken && !opcoes.silencioso && !url.startsWith("/api/acesso/chave")) {
    definirSessao(null);
    pedirEntrada().catch(() => {});
  }
  if (!r.ok) throw Object.assign(new Error(dados.erro || dados.message || "Erro " + r.status), { entrar: r.status === 401 });
  return dados;
}

const COMO = { ROSTO: "pelo rosto", CHAVE: "com a chave do Windows", NOME: "pelo nome", LOCAL: "pelo teste automático" };
const PERFIL = { COMPRADOR: "Comprador", CURADOR: "Curador", ADMIN: "Administrador" };
const podeCurar = () => !!sessao && (sessao.perfil === "CURADOR" || sessao.perfil === "ADMIN");
const ehAdmin = () => !!sessao && sessao.perfil === "ADMIN";

const fotos = new Map();
function mostrarRetrato(img, id, local) {
  img.classList.add("oculto");
  img.removeAttribute("src");
  if (local) { img.src = local; img.classList.remove("oculto"); return img; }
  if (!id || !sessao) return img;
  let p = fotos.get(id);
  if (!p) {
    p = fetch("/api/acesso/retratos/" + id, { headers: { Authorization: "Bearer " + sessao.token } })
      .then(r => r.ok ? r.blob() : Promise.reject(new Error())).then(b => URL.createObjectURL(b));
    fotos.set(id, p);
    p.catch(() => fotos.delete(id));
  }
  p.then(u => { img.src = u; img.classList.remove("oculto"); }, () => {});
  return img;
}

function retrato(id, classe) {
  const img = document.createElement("img");
  img.className = "retrato " + (classe || "");
  img.alt = "";
  return mostrarRetrato(img, id);
}

function definirSessao(s) {
  sessao = s;
  guardado.sessao(s);
  $("usuario-nome").textContent = s ? s.nome.split(" ")[0] : "Entrar";
  $("usuario").classList.toggle("logado", !!s);
  $("usuario").title = s ? `${s.nome} · entrou ${COMO[s.metodo] || ""}` : "Entrar";
  mostrarRetrato($("usuario-foto"), s && s.retrato_id, s && s.foto_local);
  $("usuario-icone").classList.toggle("oculto", !!(s && (s.retrato_id || s.foto_local)));
  document.body.dataset.perfil = s ? (s.local ? "COMPRADOR" : s.perfil || "COMPRADOR") : "";
  const aberta = document.querySelector("nav.abas button.ativa");
  if (aberta && aberta.offsetParent === null) abrir("aba-consulta");
  moverIndicador();
}

function carregar(reabrir) {
  historico();
  carregarUnidades();
  guardarCatalogo();
  if (podeCurar()) pendentes();
  verBanco();
  const aberta = document.querySelector("nav.abas button.ativa");
  if (reabrir && aberta && aberta.dataset.aba !== "aba-consulta") abrir(aberta.dataset.aba);
}

async function verBanco() {
  try {
    const r = await fetch("/api/sistema", { cache: "no-store" });
    const d = await r.json();
    const b = $("banco");
    b.classList.remove("oculto");
    b.classList.toggle("reserva", !d.persistente);
    $("banco-nome").textContent = d.persistente ? d.banco : "H2 · reserva";
    b.title = d.persistente ? `Banco ${d.banco}: guarda tudo entre um dia e outro` : "Banco de reserva H2: na memória, recomeça quando o MOKKA desliga";
  } catch (e) { $("banco").classList.add("oculto"); }
}

function pedirParaEntrar() {
  aviso("Entre para usar o MOKKA: os dados só abrem para quem entrou.", "");
  const b = document.createElement("button");
  b.type = "button";
  b.className = "principal";
  b.textContent = "Entrar";
  b.onclick = () => pedirEntrada().catch(() => {});
  $("aviso").appendChild(b);
}

const temChave = !!window.PublicKeyCredential && window.isSecureContext && !!navigator.credentials;
let esperandoEntrada = [];

function pedirEntrada() {
  if (sessao) return Promise.resolve(sessao);
  const j = $("janela-entrar");
  $("entrar-chave").classList.toggle("oculto", !temChave);
  $("entrar-info").classList.remove("erro-texto");
  $("entrar-info").textContent = window.isSecureContext ? "" : "Rosto e chave do Windows só funcionam abrindo por http://localhost:8080 ou https.";
  if (!j.open) j.showModal();
  setTimeout(() => $("entrar-nome").focus(), 50);
  return new Promise((ok, erro) => esperandoEntrada.push({ ok, erro }));
}

function entrou(s) {
  definirSessao(s);
  $("janela-entrar").close();
  esperandoEntrada.splice(0).forEach(p => p.ok(s));
  aviso(s.local ? `Olá, ${s.nome.split(" ")[0]}! Sem conexão com o servidor: você está usando o MOKKA neste computador.`
    : `Olá, ${s.nome.split(" ")[0]}! Você entrou ${COMO[s.metodo] || ""} na conta nº ${s.conta_id} · perfil ${PERFIL[s.perfil] || s.perfil}.`, "LIBERAR");
  if (fila().length) enviarFila();
  carregar(true);
}

$("janela-entrar").addEventListener("close", () => {
  if (sessao) return;
  esperandoEntrada.splice(0).forEach(p => p.erro(new Error("Entre para continuar.")));
  setTimeout(() => { if (!sessao) pedirParaEntrar(); }, 0);
});
document.querySelectorAll("dialog [data-fechar]").forEach(b => b.addEventListener("click", () => b.closest("dialog").close()));
document.querySelectorAll("dialog").forEach(d => d.addEventListener("click", e => { if (e.target === d) d.close(); }));

$("form-entrar").addEventListener("submit", async e => {
  e.preventDefault();
  const botao = e.target.querySelector("button");
  botao.classList.add("ocupado");
  try { entrou(await chamar("/api/acesso/entrar", { nome: $("entrar-nome").value.trim() }, { semToken: true })); }
  catch (err) { $("entrar-info").textContent = err.message; $("entrar-info").classList.add("erro-texto"); }
  botao.classList.remove("ocupado");
});

const b64 = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const deb64 = s => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
const erroChave = e => e && e.name === "NotAllowedError" ? "Cancelado ou o tempo acabou." : (e && e.message) || "Não deu certo.";

$("entrar-chave").addEventListener("click", async () => {
  const botao = $("entrar-chave");
  botao.classList.add("ocupado");
  $("entrar-info").classList.remove("erro-texto");
  try {
    const d = await chamar("/api/acesso/chave/desafio", {}, { semToken: true });
    const a = await navigator.credentials.get({ publicKey: { challenge: deb64(d.desafio), userVerification: "required", timeout: 60000 } });
    entrou(await chamar("/api/acesso/chave/entrar", {
      credencial_id: a.id, client_data_json: b64(a.response.clientDataJSON),
      dados_autenticador: b64(a.response.authenticatorData), assinatura: b64(a.response.signature)
    }, { semToken: true }));
  } catch (e) {
    $("entrar-info").textContent = erroChave(e);
    $("entrar-info").classList.add("erro-texto");
  }
  botao.classList.remove("ocupado");
});

$("usuario").addEventListener("click", async () => {
  if (!sessao) { pedirEntrada().catch(() => {}); return; }
  try { definirSessao(Object.assign({}, sessao, await chamar("/api/acesso/eu"))); } catch (e) { return; }
  $("conta-nome").textContent = `${sessao.nome} · conta nº ${sessao.conta_id}`;
  $("conta-info").classList.remove("erro-texto");
  $("conta-info").textContent = `Perfil ${PERFIL[sessao.perfil] || sessao.perfil}. Entrou ${COMO[sessao.metodo] || ""}.`
    + (sessao.tem_rosto ? " Rosto cadastrado." : "") + (sessao.tem_chave ? " Chave do Windows cadastrada." : "")
    + " A sessão vale até " + quando(sessao.expira) + "."
    + (sessao.metodo === "NOME" && sessao.perfil === "CURADOR" ? " Para ter o perfil de administrador, entre com o rosto ou a chave do Windows." : "");
  $("apagar-rosto").classList.toggle("oculto", !sessao.tem_rosto);
  mostrarRetrato($("conta-foto"), sessao.retrato_id, sessao.foto_local);
  $("cadastrar-rosto").innerHTML = `<svg><use href="#i-rosto"/></svg>${sessao.tem_rosto ? "Cadastrar o rosto de novo" : "Cadastrar meu rosto"}`;
  $("cadastrar-chave").classList.toggle("oculto", !temChave || sessao.tem_chave);
  $("janela-conta").showModal();
});

$("cadastrar-chave").addEventListener("click", async () => {
  const botao = $("cadastrar-chave");
  botao.classList.add("ocupado");
  try {
    const d = await chamar("/api/acesso/chave/desafio", {});
    const cred = await navigator.credentials.create({ publicKey: {
      challenge: deb64(d.desafio), rp: { name: "MOKKA" },
      user: { id: deb64(d.usuario_id), name: d.nome, displayName: d.nome },
      pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -257 }],
      authenticatorSelection: { residentKey: "required", userVerification: "required" },
      attestation: "none", timeout: 60000 } });
    const r = cred.response;
    const chave = r.getPublicKey ? r.getPublicKey() : null;
    if (!chave) throw new Error("Este navegador não entrega a chave. Use o Edge ou o Chrome atualizados.");
    const s = await chamar("/api/acesso/chave/cadastrar", { credencial_id: cred.id, chave_publica: b64(chave),
      algoritmo: r.getPublicKeyAlgorithm(), client_data_json: b64(r.clientDataJSON) });
    definirSessao(Object.assign({}, sessao, s));
    $("janela-conta").close();
    aviso("Pronto! Da próxima vez, entre com a chave do Windows. Pelo nome, ninguém mais entra como você.", "LIBERAR");
  } catch (e) { $("conta-info").textContent = erroChave(e); $("conta-info").classList.add("erro-texto"); }
  botao.classList.remove("ocupado");
});

$("apagar-rosto").addEventListener("click", async () => {
  if (!confirm("Apagar a leitura do seu rosto e todas as suas fotos guardadas? Depois você entra pelo nome e pode cadastrar de novo.")) return;
  const botao = $("apagar-rosto");
  botao.classList.add("ocupado");
  try {
    rede(1);
    const r = await fetch("/api/acesso/rosto", { method: "DELETE", headers: { Authorization: "Bearer " + sessao.token } }).finally(() => rede(-1));
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.erro || "Erro " + r.status);
    fotos.clear();
    definirSessao(Object.assign({}, sessao, d, { foto_local: null }));
    $("janela-conta").close();
    aviso("Pronto: seu rosto e suas fotos foram apagados do banco.", "LIBERAR");
  } catch (e) { $("conta-info").textContent = e.message; $("conta-info").classList.add("erro-texto"); }
  botao.classList.remove("ocupado");
});

$("sair").addEventListener("click", async () => {
  try { await chamar("/api/acesso/sair", {}); } catch (e) {}
  definirSessao(null);
  $("janela-conta").close();
  aviso("Você saiu.", "");
});

async function decidir(acao, codigo) {
  try {
    await pedirEntrada();
    const s = await chamar(`/api/solicitacoes/${atual.solicitacao_id}/decisao`, { acao, codigo, justificativa: $("justificativa").value });
    const texto = { USOU_EXISTENTE: `Pronto: use o código ${s.codigo_escolhido}.`,
                    CRIADO: `Código ${s.codigo_escolhido} criado e já está no catálogo.`,
                    AGUARDANDO_CURADOR: "Enviado ao curador: já existe código igual.",
                    GUARDADO_LOCAL: acao === "USAR_EXISTENTE" ? `Guardado neste computador: use o código ${codigo}. Envio ao sistema quando ele voltar.`
                      : "Pedido de código novo guardado neste computador. Envio ao sistema quando ele voltar." }[s.status];
    aviso(texto, s.status === "AGUARDANDO_CURADOR" || s.status === "GUARDADO_LOCAL" ? "REVISAR" : "LIBERAR");
    if (s.status === "CRIADO") $("aviso").appendChild(botaoCodigo(s.codigo_escolhido));
    document.querySelectorAll("#resultado button:not(.cod)").forEach(b => b.disabled = true);
    historico();
    pendentes();
  } catch (e) { aviso(e.message, "erro"); }
}

const ABAS = ["aba-consulta", "aba-lote", "aba-unidades", "aba-catalogo", "aba-raio-x", "aba-curador", "aba-pessoas"];
function moverIndicador() {
  const b = document.querySelector("nav.abas button.ativa"), ind = document.querySelector("nav.abas .indicador");
  if (!b || !ind) return;
  Object.assign(ind.style, { left: b.offsetLeft + "px", top: b.offsetTop + "px", width: b.offsetWidth + "px", height: b.offsetHeight + "px" });
}
addEventListener("resize", moverIndicador);

function abrir(id) {
  const botao = document.querySelector(`nav.abas button[data-aba="${id}"]`);
  if (botao && botao.offsetParent === null) id = "aba-consulta";
  document.querySelectorAll("nav.abas button").forEach(b => b.classList.toggle("ativa", b.dataset.aba === id));
  moverIndicador();
  ABAS.forEach(a => { $(a).classList.toggle("oculto", a !== id); $(a).classList.remove("entrar"); });
  void $(id).offsetWidth;
  $(id).classList.add("entrar");
  esconderAviso();
  if (!sessao) { window.scrollTo({ top: 0, behavior: "smooth" }); return; }
  if (id === "aba-catalogo" && !$("busca").value.trim()) catalogo();
  if (id === "aba-curador") { pendentes(); aprendeu(); }
  if (id === "aba-raio-x") carregarRaioX();
  if (id === "aba-pessoas") pessoas();
  window.scrollTo({ top: 0, behavior: "smooth" });
}
document.querySelectorAll("nav.abas button").forEach(b => b.addEventListener("click", () => abrir(b.dataset.aba)));

let unidades = [];
const limpar = t => t.normalize("NFKD").replace(/\p{M}/gu, "").replace(/\./g, "").trim().replace(/\s+/g, " ").toUpperCase();

function conferirUnidade() {
  const campo = $("unidade"), dica = $("unidade-dica"), texto = limpar(campo.value);
  if (!texto) { dica.textContent = ""; return; }
  const u = unidades.find(x => [x.codigo, ...x.pode_escrever].some(t => limpar(t) === texto));
  if (u) { campo.value = u.codigo; dica.textContent = u.nome; dica.className = "conta"; }
  else if (unidades.length) { dica.textContent = "Unidade desconhecida"; dica.className = "conta erro-texto"; }
}

async function carregarUnidades() {
  try {
    unidades = await chamar("/api/unidades");
    if (unidades.length) guardado.gravar("mokka-unidades", unidades);
    $("unidades").replaceChildren(...unidades.map(u => { const o = document.createElement("option"); o.value = u.codigo; o.label = u.nome; return o; }));
    const corpo = $("lista-unidades");
    corpo.replaceChildren();
    unidades.forEach(u => {
      const tr = document.createElement("tr");
      celula(tr, u.codigo).style.fontWeight = "800";
      celula(tr, u.nome + (u.aviso ? " · " + u.aviso : ""));
      celula(tr, u.pode_escrever.join(", "));
      celula(tr, u.exemplos[0] || "");
      celula(tr, u.codigos_ativos.toLocaleString("pt-BR"));
      const b = document.createElement("button");
      b.type = "button";
      b.textContent = "Usar";
      b.onclick = () => { $("unidade").value = u.codigo; conferirUnidade(); abrir("aba-consulta"); $("descricao").focus(); };
      celula(tr, b);
      corpo.appendChild(tr);
    });
    rotular(corpo);
    conferirUnidade();
  } catch (e) {}
}

function situacao(c) {
  const s = document.createElement("span");
  s.className = "selo";
  if (c.status === "BLOQUEADO") { s.className += " repetido"; s.textContent = "Repetido → " + c.codigo_canonico; }
  else s.textContent = /^(criado|importado)/.test(c.motivo || "") ? "Novo" : "Ativo";
  return s;
}

async function visaoGeral() {
  try {
    const [r, grupos] = await Promise.all([chamar("/api/codigos/resumo"), chamar("/api/codigos/padronizados")]);
    const cartoes = [[r.na_base, "na base"], [r.oficiais, "oficiais"], [r.repetidos, "repetidos achados"],
      [r.criados, "criados agora"], [r.consultas, "consultas"], [r.duplicados_evitados, "duplicados evitados", true]];
    $("numeros").replaceChildren(...cartoes.map(([n, t, d]) => {
      const div = document.createElement("div"), b = document.createElement("b"), s = document.createElement("span");
      if (d) div.className = "destaque";
      contar(b, n); s.textContent = t; div.append(b, s); return div; }));
    $("grupos").replaceChildren(...grupos.slice(0, 6).map(g => {
      const d = document.createElement("div"), o = document.createElement("div");
      d.className = "grupo"; o.className = "oficial";
      o.append(botaoCodigo(g.oficial.codigo), ` ${M(g.oficial.descricao)} [${g.oficial.unidade}]`);
      d.appendChild(o);
      g.repetidos.slice(0, 4).forEach(x => { const l = document.createElement("div"); l.className = "repetido"; l.textContent = `${x.codigo} · ${M(x.descricao)}`; d.appendChild(l); });
      if (g.repetidos.length > 4) { const l = document.createElement("div"); l.className = "repetido"; l.textContent = `+ ${g.repetidos.length - 4}`; d.appendChild(l); }
      return d; }));
  } catch (e) {}
}

async function catalogo(busca) {
  $("visao-geral").classList.toggle("oculto", !!busca);
  if (!busca) visaoGeral();
  try {
    const lista = await chamar(busca ? "/api/codigos?busca=" + encodeURIComponent(busca) : "/api/codigos/novos");
    $("titulo-catalogo").textContent = busca ? `Resultado para "${busca}"` : "Criados agora";
    const corpo = $("lista-catalogo");
    corpo.replaceChildren();
    lista.forEach(c => {
      const tr = document.createElement("tr");
      celula(tr, botaoCodigo(c.codigo));
      [M(c.descricao), c.unidade, c.grupo, c.criado_por || "base"].forEach(v => celula(tr, v));
      celula(tr, situacao(c));
      corpo.appendChild(tr);
    });
    rotular(corpo);
    $("catalogo-vazio").textContent = busca ? "Nada encontrado." : "Nenhum código criado ainda.";
    $("catalogo-vazio").classList.toggle("oculto", lista.length > 0);
    corpo.closest(".rolagem").classList.toggle("oculto", !lista.length);
  } catch (e) { aviso(e.message, "erro"); }
}

const STATUS = { USOU_EXISTENTE: "usou", CRIADO: "criou", AGUARDANDO_CURADOR: "no curador", RECUSADO: "recusado → usou", PENDENTE: "consultou" };

async function ficha(codigo) {
  const j = $("janela-ficha"), corpo = $("ficha");
  $("ficha-titulo").textContent = "Código " + codigo;
  corpo.replaceChildren();
  if (!j.open) j.showModal();
  try {
    const d = await chamar("/api/codigos/" + encodeURIComponent(codigo));
    const c = d.codigo;
    const criou = d.usos.find(u => u.status === "CRIADO");
    const grande = document.createElement("div");
    grande.className = "grande";
    grande.textContent = M(c.descricao);
    const dl = document.createElement("dl");
    const item = (k, v) => { if (v === null || v === undefined || v === "") return; const dt = document.createElement("dt"), dd = document.createElement("dd"); dt.textContent = k; if (v instanceof Node) dd.appendChild(v); else dd.textContent = v; dl.append(dt, dd); };
    item("Nome padrão", c.descricao_padrao);
    item("Unidade", c.unidade);
    item("Grupo", c.grupo);
    item("Categoria", c.categoria);
    item("Situação", situacao(c));
    if (d.oficial) item("Use no lugar", botaoCodigo(d.oficial.codigo));
    if (c.criado_por) {
      const quem = document.createElement("span");
      quem.className = "pessoa";
      if (criou && criou.retrato_id) quem.append(retrato(criou.retrato_id, "medio"));
      quem.append(c.criado_por + (criou && COMO[criou.metodo] ? ", entrou " + COMO[criou.metodo] : ""));
      item("Criado por", quem);
    } else item("Criado por", "base original da Gerdau");
    item("Criado em", quando(c.criado_em));
    item("Origem", c.motivo);
    item("Fornecedores", d.fornecedores.length ? d.fornecedores.length + ": " + d.fornecedores.slice(0, 6).join(", ") + (d.fornecedores.length > 6 ? "…" : "") : "");
    corpo.append(grande, dl);
    if (d.repetidos.length) {
      const h = document.createElement("h3"), ol = document.createElement("ol");
      h.textContent = `Repetidos que apontam para ele (${d.repetidos.length})`;
      d.repetidos.slice(0, 8).forEach(x => { const li = document.createElement("li"); li.textContent = `${x.codigo} · ${M(x.descricao)}`; ol.appendChild(li); });
      corpo.append(h, ol);
    }
    const h = document.createElement("h3"), ol = document.createElement("ol");
    h.textContent = "Quem usou";
    d.usos.forEach(u => {
      const li = document.createElement("li");
      if (u.retrato_id) li.append(retrato(u.retrato_id, "mini"));
      li.append(`${u.solicitante} ${STATUS[u.status] || u.status} · ${quando(u.criada_em)}${u.metodo === "ROSTO" ? " · pelo rosto" : u.metodo === "CHAVE" ? " · chave do Windows" : ""}${u.curador ? " · curador " + u.curador : ""}`);
      ol.appendChild(li);
    });
    if (d.usos.length) corpo.append(h, ol);
    else { const p = document.createElement("p"); p.className = "info"; p.textContent = "Ninguém usou pelo MOKKA ainda."; corpo.append(h, p); }
  } catch (e) { corpo.textContent = e.message; }
}

let gruposCurador = [];
const CONFIANCA = 80;
const ACAO = { APROVAR: "Aprovar", RECUSAR: "Recusar" };

function certeza(p) {
  const s = aderencia(p);
  s.classList.add("certeza");
  return s;
}

async function pendentes() {
  if (!podeCurar()) return;
  try {
    gruposCurador = await chamar("/api/solicitacoes/curador");
    const total = gruposCurador.reduce((n, g) => n + g.ids.length, 0);
    $("qtd-curador").textContent = total;
    $("qtd-curador").classList.toggle("oculto", !total);
    moverIndicador();
    const lista = $("lista-curador");
    lista.replaceChildren();
    gruposCurador.forEach(g => {
      const card = document.createElement("article");
      card.className = "pedido";
      const topo = document.createElement("div");
      topo.className = "pedido-topo";
      const info = document.createElement("div");
      if (g.ids.length > 1) { const q = document.createElement("span"); q.className = "quantos"; q.textContent = `${g.ids.length} pedidos iguais`; info.appendChild(q); }
      const h = document.createElement("h3");
      h.textContent = `${M(g.descricao)} [${g.unidade}]`;
      const meta = document.createElement("div");
      meta.className = "meta";
      meta.append("Já existe ", botaoCodigo(g.codigo_existente), ` · pediram: ${g.pediram.join(", ")}`);
      info.append(h, meta);
      const acoes = document.createElement("div");
      acoes.className = "acoes linha-botoes";
      const n = g.ids.length > 1 ? ` os ${g.ids.length}` : "";
      const sug = g.sugestao;
      [[`Aprovar${n}`, true, "APROVAR"], [`Recusar${n}`, false, "RECUSAR"]].forEach(([nome, aprovar, acao]) => {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = nome;
        b.className = sug && sug.acao === acao ? "principal" : "";
        b.onclick = () => curadoria(g.ids, aprovar, b);
        acoes.appendChild(b);
      });
      topo.append(info, acoes);
      if (sug) {
        const linha = document.createElement("div");
        linha.className = "sugestao " + sug.acao + (sug.certeza >= CONFIANCA ? " forte" : "");
        const t = document.createElement("b");
        t.textContent = "Sugestão do MOKKA: " + ACAO[sug.acao];
        const m = document.createElement("span");
        m.textContent = sug.motivo;
        linha.append(t, certeza(sug.certeza), m);
        topo.appendChild(linha);
      }
      const ul = document.createElement("ul");
      g.pedidos.forEach(p => {
        const li = document.createElement("li");
        const b = document.createElement("b");
        b.textContent = M(p.descricao_digitada);
        const em = document.createElement("em");
        em.textContent = `${p.solicitante} · ${quando(p.criada_em)}${p.justificativa ? " · “" + p.justificativa + "”" : ""}`;
        li.append(b, em);
        if (g.ids.length > 1) {
          const so = document.createElement("button");
          so.type = "button";
          so.className = "link";
          so.textContent = "decidir só este";
          so.onclick = () => { const r = confirm(`Aprovar só o pedido de ${p.solicitante}? (Cancelar = recusar só este)`); curadoria([p.id], r, so); };
          li.appendChild(so);
        }
        ul.appendChild(li);
      });
      card.style.setProperty("--i", lista.children.length);
      card.append(topo, ul);
      lista.appendChild(card);
    });
    $("curador-vazio").classList.toggle("oculto", gruposCurador.length > 0);
    const fortes = gruposCurador.filter(g => g.sugestao && g.sugestao.certeza >= CONFIANCA);
    $("barra-curador").classList.toggle("oculto", !fortes.length);
    $("aceitar-sugestoes").textContent = fortes.length === 1 ? "Aceitar a sugestão forte" : `Aceitar as ${fortes.length} sugestões fortes`;
    $("sugestoes-info").textContent = `Só as que o MOKKA tem ${CONFIANCA}% ou mais de certeza. Dá para desfazer depois.`;
  } catch (e) {}
}

function desfazer(ids) {
  const b = document.createElement("button");
  b.type = "button";
  b.textContent = "Desfazer";
  b.onclick = async () => {
    b.classList.add("ocupado");
    try {
      await chamar("/api/solicitacoes/curador/desfazer", { ids });
      aviso(`Desfeito: ${ids.length > 1 ? ids.length + " pedidos voltaram" : "o pedido voltou"} para o curador, e o MOKKA esqueceu o que tinha aprendido com isso.`, "REVISAR");
      pendentes();
      aprendeu();
      historico();
    } catch (e) { aviso(e.message, "erro"); }
  };
  $("aviso").appendChild(b);
}

async function curadoria(ids, aprovar, botao) {
  try {
    await pedirEntrada();
    botao.classList.add("ocupado");
    const r = await chamar("/api/solicitacoes/curador/grupo", { ids, aprovar });
    const cod = r[0].codigo_escolhido;
    aviso(aprovar ? `Aprovado: ${r.length > 1 ? r.length + " pedidos usam" : "criado"} o código ${cod}.` : `Recusado: ${r.length > 1 ? "os " + r.length + " pedidos usam" : "use"} o ${cod}.`, aprovar ? "LIBERAR" : "REVISAR");
    $("aviso").appendChild(botaoCodigo(cod));
    desfazer(r.map(x => x.id));
    pendentes();
    aprendeu();
    historico();
  } catch (e) { aviso(e.message, "erro"); }
  botao.classList.remove("ocupado");
}

$("aceitar-sugestoes").addEventListener("click", async () => {
  const fortes = gruposCurador.filter(g => g.sugestao && g.sugestao.certeza >= CONFIANCA);
  if (!fortes.length) return;
  const aprovar = fortes.filter(g => g.sugestao.acao === "APROVAR").length;
  if (!confirm(`Decidir ${fortes.length} grupo(s) como o MOKKA sugeriu? ${aprovar} aprovado(s) e ${fortes.length - aprovar} recusado(s).`)) return;
  const botao = $("aceitar-sugestoes");
  botao.classList.add("ocupado");
  const feitos = [];
  let erro = null;
  for (const g of fortes) {
    try { feitos.push(...(await chamar("/api/solicitacoes/curador/grupo", { ids: g.ids, aprovar: g.sugestao.acao === "APROVAR" })).map(x => x.id)); }
    catch (e) { erro = e; }
  }
  botao.classList.remove("ocupado");
  aviso(`${feitos.length} pedido(s) decididos pelas sugestões.` + (erro ? " Alguns não deram certo: " + erro.message : ""), erro ? "erro" : "LIBERAR");
  if (feitos.length) desfazer(feitos);
  pendentes();
  aprendeu();
  historico();
});

function itemRegra(partes, botoes) {
  const li = document.createElement("li");
  const texto = document.createElement("div");
  texto.className = "regra-texto";
  texto.append(...partes);
  li.appendChild(texto);
  if (botoes.length) {
    const b = document.createElement("div");
    b.className = "regra-botoes";
    b.append(...botoes);
    li.appendChild(b);
  }
  return li;
}

function botaoPequeno(texto, classe, acao) {
  const b = document.createElement("button");
  b.type = "button";
  b.className = classe;
  b.textContent = texto;
  b.onclick = async () => {
    b.classList.add("ocupado");
    try { await acao(); aprendeu(); } catch (e) { aviso(e.message, "erro"); }
    b.classList.remove("ocupado");
  };
  return b;
}

async function aprendeu() {
  if (!podeCurar()) return;
  try {
    const d = await chamar("/api/aprendizado");
    const regras = $("lista-regras"), palavras = $("lista-palavras");
    regras.replaceChildren(...d.regras.slice(0, 40).map((x, i) => {
      const tipo = document.createElement("span");
      tipo.className = "tipo " + x.tipo;
      tipo.textContent = x.tipo === "IGUAL" ? "Igual a" : "Diferente de";
      const desc = document.createElement("b");
      desc.textContent = `${x.descricao} [${x.unidade}]`;
      const meta = document.createElement("em");
      meta.textContent = `${x.votos} ${x.votos === 1 ? "decisão" : "decisões"}${x.ultimo_por ? " · " + x.ultimo_por : ""}`;
      const li = itemRegra([desc, tipo, botaoCodigo(x.codigo), meta], [botaoPequeno("Esquecer", "link", async () => {
        rede(1);
        const r = await fetch("/api/aprendizado/regras/" + x.id, { method: "DELETE", headers: { Authorization: "Bearer " + sessao.token } }).finally(() => rede(-1));
        if (!r.ok) throw new Error((await r.json().catch(() => ({}))).erro || "Erro " + r.status);
      })]);
      li.style.setProperty("--i", i);
      return li;
    }));
    palavras.replaceChildren(...d.palavras.slice(0, 40).map((x, i) => {
      const de = document.createElement("b");
      de.textContent = x.termo;
      const para = document.createElement("b");
      para.textContent = x.correcao;
      const seta = document.createElement("span");
      seta.className = "seta";
      seta.textContent = "→";
      const selo = document.createElement("span");
      selo.className = "selo " + x.status;
      selo.textContent = { SUGERIDA: "Esperando curador", ACEITA: "Vale", RECUSADA: "Recusada" }[x.status];
      const meta = document.createElement("em");
      meta.textContent = `${x.votos} ${x.votos === 1 ? "vez" : "vezes"}${x.ultimo_por ? " · " + x.ultimo_por : ""}`;
      const decidir = aceitar => () => chamar(`/api/aprendizado/palavras/${encodeURIComponent(x.termo)}/decisao`, { aceitar });
      const botoes = x.status === "SUGERIDA" ? [botaoPequeno("Aceitar", "principal", decidir(true)), botaoPequeno("Recusar", "", decidir(false))]
        : [botaoPequeno(x.status === "ACEITA" ? "Recusar" : "Aceitar", "link", decidir(x.status !== "ACEITA"))];
      const li = itemRegra([de, seta, para, selo, meta], botoes);
      li.style.setProperty("--i", i);
      return li;
    }));
    $("regras-vazio").classList.toggle("oculto", d.regras.length > 0);
    $("palavras-vazio").classList.toggle("oculto", d.palavras.length > 0);
  } catch (e) {}
}

async function pessoas() {
  if (!ehAdmin()) return;
  try {
    const [lista, registro] = await Promise.all([chamar("/api/admin/pessoas"), chamar("/api/admin/auditoria")]);
    const corpo = $("lista-pessoas");
    corpo.replaceChildren();
    lista.forEach(u => {
      const tr = document.createElement("tr");
      celula(tr, "nº " + u.id);
      celula(tr, u.nome).style.fontWeight = "700";
      const sel = document.createElement("select");
      Object.entries(PERFIL).forEach(([v, t]) => { const o = document.createElement("option"); o.value = v; o.textContent = t; sel.appendChild(o); });
      sel.value = u.perfil;
      sel.disabled = u.admin_fixo;
      sel.title = u.admin_fixo ? "Administrador da equipe (lista fixa no MOKKA)" : "Mudar o perfil";
      sel.onchange = async () => {
        try { await chamar(`/api/admin/pessoas/${u.id}/perfil`, { perfil: sel.value }); aviso(`${u.nome} agora é ${PERFIL[sel.value]}.`, "LIBERAR"); pessoas(); }
        catch (e) { aviso(e.message, "erro"); sel.value = u.perfil; }
      };
      celula(tr, sel);
      celula(tr, [u.tem_rosto && "rosto", u.tem_chave && "chave do Windows"].filter(Boolean).join(" e ") || "nome");
      celula(tr, quando(u.criado_em));
      corpo.appendChild(tr);
    });
    rotular(corpo);
    const aud = $("lista-auditoria");
    aud.replaceChildren();
    registro.slice(0, 60).forEach(a => {
      const tr = document.createElement("tr");
      celula(tr, quando(a.quando));
      celula(tr, a.quem || "—");
      celula(tr, NOME_ACAO[a.acao] || a.acao, /RECUSADA|BLOQUEIO/.test(a.acao) ? "res BLOQUEAR" : "");
      celula(tr, a.alvo);
      celula(tr, a.detalhe);
      celula(tr, a.ip);
      aud.appendChild(tr);
    });
    rotular(aud);
  } catch (e) { aviso(e.message, "erro"); }
}

const NOME_ACAO = { ENTROU: "Entrou", ENTRADA_RECUSADA: "Entrada recusada", BLOQUEIO_FORCA_BRUTA: "Bloqueado por tentativas", CRIOU_CODIGO: "Criou código",
  APROVOU: "Curador aprovou", RECUSOU: "Curador recusou", DESFEZ: "Desfez decisão", MUDOU_PERFIL: "Mudou perfil", CADASTROU_ROSTO: "Cadastrou rosto",
  APAGOU_ROSTO: "Apagou rosto e fotos", ACEITOU_PALAVRA: "Aceitou palavra", RECUSOU_PALAVRA: "Recusou palavra", ESQUECEU_REGRA: "Esqueceu regra",
  DESBLOQUEOU: "Desbloqueou entradas", ZEROU_BANCO: "Zerou o banco" };

$("desbloquear").addEventListener("click", async () => {
  try { await chamar("/api/admin/desbloquear", {}); aviso("Pronto: quem errou a entrada pode tentar de novo.", "LIBERAR"); pessoas(); }
  catch (e) { aviso(e.message, "erro"); }
});

$("zerar").addEventListener("click", async () => {
  if (!confirm("Zerar o banco? Apaga pedidos, pessoas, fotos, aprendizado e códigos criados, e carrega a base de novo.")) return;
  const botao = $("zerar");
  botao.classList.add("ocupado");
  try {
    const r = await chamar("/api/admin/zerar", {});
    aviso(`Banco ${r.banco} zerado e carregado de novo a partir da base.`, "LIBERAR");
    historico();
    pessoas();
  } catch (e) { aviso(e.message, "erro"); }
  botao.classList.remove("ocupado");
});

function aderencia(p) {
  const s = document.createElement("span");
  s.className = "aderencia";
  s.textContent = p + "%";
  const i = document.createElement("i");
  i.style.setProperty("--p", p + "%");
  s.appendChild(i);
  return s;
}

function mostrar(r) {
  atual = r;
  aviso(r.motivo, r.resultado);
  if (r.sugestao) {
    const s = r.sugestao, a = $("aviso");
    a.className = "REVISAR mostra";
    a.textContent = `Você quis dizer "${M(s.descricao)}"? ${s.motivo}.`;
    const b = document.createElement("button");
    b.type = "button";
    b.className = "principal";
    b.textContent = "Sim, consultar assim";
    b.onclick = () => { (s.correcoes || []).forEach(c => ensinar(c.de, c.para)); $("descricao").value = s.descricao; contarCaracteres(); enviarFormulario($("consulta")); };
    a.appendChild(b);
  }
  const corpo = $("candidatos");
  corpo.replaceChildren();
  r.candidatos.slice(0, 6).forEach((c, i) => {
    const tr = document.createElement("tr");
    celula(tr, botaoCodigo(c.codigo));
    celula(tr, M(c.descricao));
    celula(tr, c.unidade);
    celula(tr, aderencia(c.aderencia));
    const muda = document.createElement("span");
    const classe = document.createElement("span");
    classe.className = "classe " + c.classe;
    classe.textContent = { DUPLICADO: "Igual", REVISAR: "Quase igual", DIFERENTE: "Diferente" }[c.classe];
    const aprendido = c.diferencas.find(d => d.startsWith("aprendido"));
    if (aprendido) { const a = document.createElement("span"); a.className = "selo aprendido"; a.textContent = "Aprendido"; a.title = aprendido; muda.append(a, " "); }
    const outras = c.diferencas.filter(d => d !== aprendido);
    muda.append(classe, outras.length ? " · " + outras.join(", ") : "");
    celula(tr, muda);
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = "Usar este";
    if (i === 0 && r.resultado !== "LIBERAR") b.className = "principal";
    b.onclick = () => decidir("USAR_EXISTENTE", c.codigo);
    celula(tr, b);
    corpo.appendChild(tr);
  });
  rotular(corpo);
  $("resultado").classList.remove("oculto");
  $("justificativa").value = "";
  $("justificativa").required = r.resultado !== "LIBERAR";
  $("justificativa").classList.toggle("oculto", r.resultado === "LIBERAR");
  $("rotulo-justificativa").textContent = r.resultado === "LIBERAR" ? "Nenhum igual: pode criar." : "Nenhum serve? Diga por que é diferente.";
  $("criar").className = r.resultado === "LIBERAR" ? "principal" : "";
  $("criar").disabled = false;
  const pediuCriar = pedidoDeCriar;
  pedidoDeCriar = false;
  if (pediuCriar && !r.sugestao) {
    aviso(r.motivo + (r.resultado === "LIBERAR" ? " · Você pediu para criar: confira e clique em Criar." : " · Já existe igual ou parecido: use um ou justifique."), r.resultado);
    $(r.resultado === "LIBERAR" ? "criar" : "justificativa").scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

async function historico() {
  try {
    const lista = await chamar("/api/solicitacoes");
    const corpo = $("historico");
    corpo.replaceChildren();
    lista.slice(0, 20).forEach(s => {
      const tr = document.createElement("tr");
      [s.id, M(s.descricao_digitada), s.solicitante + (s.metodo === "CHAVE" ? " 🔒" : "")].forEach(v => celula(tr, v));
      celula(tr, { BLOQUEAR: "Já existe", REVISAR: "Parecido", LIBERAR: "Novo" }[s.resultado] || s.resultado, "res " + s.resultado);
      celula(tr, botaoCodigo(s.codigo_escolhido));
      celula(tr, STATUS[s.status] || s.status);
      corpo.appendChild(tr);
    });
    rotular(corpo);
  } catch (e) {}
}

$("form-catalogo").addEventListener("submit", e => {
  e.preventDefault();
  const busca = $("busca").value.trim();
  if (busca.length < 2) { aviso("Digite pelo menos 2 letras ou números.", "erro"); return; }
  esconderAviso();
  catalogo(busca);
});

$("unidade").addEventListener("change", conferirUnidade);

function maiusculas(campo) {
  const p = campo.selectionStart;
  campo.value = campo.value.toUpperCase();
  campo.setSelectionRange(p, p);
}

function contarCaracteres() {
  const n = $("descricao").value.length;
  $("conta").textContent = n > 40 ? `${n}/40 · ao consultar, abrevio no padrão do SAP` : n + "/40";
  $("conta").classList.toggle("cheio", n === 40);
  $("conta").classList.toggle("passou", n > 40);
}

$("descricao").addEventListener("input", e => {
  maiusculas(e.target);
  contarCaracteres();
  if (falado) { falado = null; desenharPalavras(); }
});
$("busca").addEventListener("input", e => maiusculas(e.target));

$("consulta").addEventListener("submit", async e => {
  e.preventDefault();
  conferirUnidade();
  const botao = $("consulta").querySelector("button.principal");
  try {
    await pedirEntrada();
    if ($("descricao").value.length > 40) {
      const r = caber40($("descricao").value);
      $("descricao").value = r.texto;
      contarCaracteres();
      aviso(r.cortou ? `Mesmo abreviando passou de 40; cortei o final, confira: ${r.texto}` : `Abreviei para caber nos 40 do SAP: ${r.original} → ${r.texto}`, r.cortou ? "REVISAR" : "");
    }
    botao.classList.add("ocupado");
    const pedido = { descricao: $("descricao").value, unidade: $("unidade").value, grupo: $("grupo").value };
    const r = await chamar("/api/consultas", pedido);
    r.pedido = pedido;
    mostrar(r);
    historico();
  } catch (err) { aviso(err.message, "erro"); }
  botao.classList.remove("ocupado");
});

$("criar").addEventListener("click", () => {
  if ($("justificativa").required && !$("justificativa").value.trim()) {
    aviso("Escreva a justificativa: já existe código igual ou parecido.", "erro");
    $("justificativa").focus();
    return;
  }
  decidir("CRIAR_NOVO");
});

const Reconhecedor = window.SpeechRecognition || window.webkitSpeechRecognition;
const celular = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
const SAFARI = /Safari\//.test(navigator.userAgent) && !/Chrome|Chromium|CriOS|Edg|OPR|FxiOS/.test(navigator.userAgent);
const FIREFOX = /Firefox\/|FxiOS/.test(navigator.userAgent);
const enviarFormulario = f => f.requestSubmit ? f.requestSubmit() : f.dispatchEvent(new Event("submit", { cancelable: true }));
const aceitaFiltro = !!Reconhecedor && typeof Reconhecedor.available === "function";
let ouvindo = null;
let audio = null;
const EDGE = "microsoft-edge:" + location.href.split("#")[0];

const filtro = $("filtro-ruido");
filtro.checked = aceitaFiltro && guardado.ler("mokka-filtro") === true;
filtro.disabled = !aceitaFiltro;
if (!aceitaFiltro) filtro.closest("label").title = "Seu navegador ainda não deixa filtrar a voz. Atualize o Edge ou o Chrome.";
filtro.addEventListener("change", () => {
  guardado.gravar("mokka-filtro", filtro.checked);
  aviso(filtro.checked ? "Anti-ruído ligado: corta zumbido, eco e barulho de fundo." : "Anti-ruído desligado: voz do jeito que o microfone capta.", "");
});

async function abrirAudio(comFiltro) {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return null;
  const stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, noiseSuppression: comFiltro, echoCancellation: comFiltro, autoGainControl: true } });
  const ctx = new (window.AudioContext || window.webkitAudioContext)();
  const fonte = ctx.createMediaStreamSource(stream);
  let saida = fonte;
  if (comFiltro) {
    const graves = ctx.createBiquadFilter();
    graves.type = "highpass";
    graves.frequency.value = 100;
    const agudos = ctx.createBiquadFilter();
    agudos.type = "lowpass";
    agudos.frequency.value = 7000;
    const compressor = ctx.createDynamicsCompressor();
    compressor.threshold.value = -40;
    compressor.ratio.value = 4;
    fonte.connect(graves);
    graves.connect(agudos);
    agudos.connect(compressor);
    saida = compressor;
  }
  const analisador = ctx.createAnalyser();
  analisador.fftSize = 512;
  saida.connect(analisador);
  const destino = ctx.createMediaStreamDestination();
  saida.connect(destino);
  return { ctx, stream, analisador, trilha: destino.stream.getAudioTracks()[0], maximo: 0, inicio: performance.now(), avisou: false };
}

function medir() {
  if (!audio) return;
  const dados = new Uint8Array(audio.analisador.fftSize);
  audio.analisador.getByteTimeDomainData(dados);
  let soma = 0;
  for (const v of dados) soma += ((v - 128) / 128) ** 2;
  const nivel = Math.min(1, Math.sqrt(soma / dados.length) * 4);
  audio.maximo = Math.max(audio.maximo, nivel);
  const barras = $("nivel").children;
  [...barras].forEach((b, i) => { b.style.height = Math.max(3, Math.min(16, nivel * 16 * (1 + Math.sin(i * 1.7 + performance.now() / 120) * .35))) + "px"; });
  if (!audio.avisou && performance.now() - audio.inicio > 2500 && audio.maximo < .05) {
    audio.avisou = true;
    aviso("Estou ouvindo muito baixo: chegue mais perto do microfone" + (aceitaFiltro && !filtro.checked ? " ou ligue o Anti-ruído." : "."), "REVISAR");
  }
  audio.quadro = requestAnimationFrame(medir);
}

function fecharAudio() {
  if (!audio) return;
  cancelAnimationFrame(audio.quadro);
  audio.stream.getTracks().forEach(t => t.stop());
  audio.ctx.close().catch(() => {});
  audio = null;
  $("nivel").classList.remove("ativo");
  [...$("nivel").children].forEach(b => b.style.height = "3px");
}

function avisoNavegador(texto) {
  aviso(texto, "erro");
  if (/Windows/.test(navigator.userAgent) && !/Edg\//.test(navigator.userAgent)) {
    const a = document.createElement("a");
    a.href = EDGE;
    a.textContent = "Abrir no Edge";
    $("aviso").appendChild(a);
  }
}

function prepararMic(botao, aoOuvir) {
  botao.innerHTML = '<svg><use href="#i-mic"/></svg><span>Falar</span>';
  botao.title = "Falar";
  if (!Reconhecedor || !window.isSecureContext) {
    botao.classList.add("sem-voz");
    botao.title = !Reconhecedor ? "Este navegador não tem reconhecimento de voz." : "O microfone só funciona em http://localhost:8080 neste computador.";
    botao.addEventListener("click", () => aviso(!Reconhecedor
      ? (FIREFOX ? "O Firefox ainda não tem reconhecimento de voz. Para falar, abra no Chrome, no Edge ou no Safari; aqui, digite a descrição."
        : "Este navegador não tem reconhecimento de voz. Para falar, abra no Chrome, no Edge ou no Safari; aqui, digite a descrição.")
      : "O microfone só funciona abrindo por http://localhost:8080 no computador que liga o MOKKA. Aqui, digite a descrição.", "REVISAR"));
    return;
  }
  botao.addEventListener("click", async () => {
    if (ouvindo) { ouvindo.stop(); return; }
    if (navigator.brave) { avisoNavegador("O Brave bloqueia a voz. Use o Edge ou o Chrome."); return; }
    const r = new Reconhecedor();
    r.lang = "pt-BR";
    r.interimResults = true;
    r.maxAlternatives = 5;
    r.continuous = false;
    ouvindo = r;
    botao.classList.add("ouvindo");
    botao.querySelector("span").textContent = "Ouvindo";
    aviso("Pode falar. Ex.: \"soldador hora extra cinquenta por cento, unidade hora\".", "");
    const usarFiltro = filtro.checked && aceitaFiltro;
    if (usarFiltro) {
      try { audio = await abrirAudio(true); $("nivel").classList.add("ativo"); medir(); } catch (e) { audio = null; }
    }
    if (!navigator.onLine && Reconhecedor.available) {
      try { if (await Reconhecedor.available({ langs: ["pt-BR"], processLocally: true }) === "available") r.processLocally = true; } catch (e) {}
    }
    r.onresult = e => {
      const res = e.results[e.results.length - 1];
      aoOuvir(Array.from(res).map(a => a.transcript), res.isFinal, res[0] && res[0].confidence);
    };
    r.onerror = e => {
      const msg = { "not-allowed": "O navegador bloqueou o microfone. Clique no cadeado da barra de endereço e permita.",
                    "service-not-allowed": SAFARI ? "No Mac, ative o Ditado em Ajustes do Sistema › Teclado › Ditado e permita o microfone para o Safari."
                      : "O navegador bloqueou o microfone. Clique no cadeado da barra de endereço e permita.",
                    "no-speech": "Não ouvi nada. Tente de novo, mais perto do microfone.",
                    "audio-capture": "Nenhum microfone encontrado.",
                    "network": "A voz do navegador precisa de internet e só funciona no Edge, no Chrome ou no Safari." }[e.error];
      if (e.error === "network") avisoNavegador(msg);
      else if (msg) aviso(msg, "erro");
    };
    r.onend = () => {
      ouvindo = null;
      fecharAudio();
      botao.classList.remove("ouvindo");
      botao.querySelector("span").textContent = "Falar";
    };
    try {
      if (usarFiltro && audio) r.start(audio.trilha); else r.start();
    } catch (e) { fecharAudio(); r.onend(); aviso("Não consegui ligar o microfone: " + e.message, "erro"); return; }
    if (!usarFiltro && !celular && !SAFARI) {
      abrirAudio(false).then(a => {
        if (!a) return;
        if (ouvindo !== r || audio) { a.stream.getTracks().forEach(t => t.stop()); a.ctx.close().catch(() => {}); return; }
        audio = a;
        $("nivel").classList.add("ativo");
        medir();
      }).catch(() => {});
    }
  });
}

function usarFala(f) {
  $("descricao").value = f.texto;
  contarCaracteres();
  if (f.unidade) { $("unidade").value = f.unidade; conferirUnidade(); }
}

function diffPalavras(a, b) {
  const n = a.length, m = b.length, L = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) L[i][j] = a[i] === b[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
  const blocos = [];
  let i = 0, j = 0, bi = null, bj = null;
  const fechar = () => { if (bi !== null) { blocos.push([bi, i, bj, j]); bi = bj = null; } };
  while (i < n || j < m) {
    if (i < n && j < m && a[i] === b[j]) { fechar(); i++; j++; }
    else {
      if (bi === null) { bi = i; bj = j; }
      if (j < m && (i >= n || L[i][j + 1] >= L[i + 1][j])) j++; else i++;
    }
  }
  fechar();
  return blocos;
}

let falado = null;

function juntarSugestao(mapa, indice, tamanho, sugestoes) {
  const v = mapa.get(indice) || { tamanho, sugestoes: [] };
  if (tamanho > v.tamanho) v.tamanho = tamanho;
  for (const s of sugestoes) if (s && !v.sugestoes.includes(s)) v.sugestoes.push(s);
  mapa.set(indice, v);
}

async function palavrasDuvidosas(texto) {
  try { return (await chamar("/api/consultas/palavras", { texto })).palavras; } catch (e) { return []; }
}

async function escolherMelhor(alternativas) {
  const convertidas = alternativas.map(a => falaParaTexto(a)).filter(f => f.texto);
  if (convertidas.length < 2) return { melhor: convertidas[0], outras: [] };
  const duvidas = await Promise.all(convertidas.map(f => palavrasDuvidosas(f.texto)));
  let melhor = 0;
  duvidas.forEach((d, i) => { if (d.length < duvidas[melhor].length) melhor = i; });
  return { melhor: convertidas[melhor], duvidas: duvidas[melhor], outras: convertidas.filter((_, i) => i !== melhor) };
}

function desenharPalavras(animar = true) {
  const caixa = $("palavras");
  caixa.replaceChildren();
  caixa.classList.toggle("parado", !animar);
  if (!falado) { $("falado").classList.add("oculto"); return; }
  const palavras = $("descricao").value.split(" ").filter(Boolean);
  caixa.classList.toggle("tem-duvida", falado.mapa.size > 0);
  for (let i = 0; i < palavras.length;) {
    const s = falado.mapa.get(i);
    const tam = s ? s.tamanho : 1;
    const chip = document.createElement(s ? "button" : "span");
    chip.className = "palavra" + (s ? " duvida" : "");
    chip.textContent = palavras.slice(i, i + tam).join(" ");
    chip.style.setProperty("--i", i);
    if (s) {
      const indice = i;
      const envolve = document.createElement("span");
      envolve.className = "caixa-duvida";
      const bolha = document.createElement("button");
      bolha.type = "button";
      bolha.className = "bolha";
      bolha.textContent = s.sugestoes[0] + "?";
      bolha.title = "Trocar por " + s.sugestoes[0];
      bolha.onclick = e => { e.stopPropagation(); trocarPalavra(indice, s.tamanho, s.sugestoes[0]); };
      chip.type = "button";
      chip.title = "Clique para ver mais opções";
      chip.onclick = e => { e.stopPropagation(); abrirOpcoes(envolve, indice, s); };
      envolve.append(bolha, chip);
      caixa.appendChild(envolve);
    } else caixa.appendChild(chip);
    i += tam;
  }
  if (!falado.mapa.size) $("falado-nota").textContent = $("falado-nota").textContent.split(" · ").filter(t => !t.startsWith("palavras amarelas")).join(" · ");
  $("falado").classList.remove("oculto");
}

function fecharOpcoes() {
  document.querySelectorAll(".palavra.aberta").forEach(c => c.classList.remove("aberta"));
  document.querySelectorAll(".opcoes:not(.saindo)").forEach(o => {
    o.classList.add("saindo");
    setTimeout(() => o.remove(), 160);
  });
}
addEventListener("keydown", e => { if (e.key === "Escape") fecharOpcoes(); });

function abrirOpcoes(envolve, indice, s) {
  fecharOpcoes();
  const menu = document.createElement("div");
  menu.className = "opcoes";
  envolve.querySelector(".palavra").classList.add("aberta");
  const titulo = document.createElement("span");
  titulo.textContent = "Você quis dizer:";
  menu.appendChild(titulo);
  s.sugestoes.slice(0, 4).forEach((sug, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = sug;
    b.style.setProperty("--i", i);
    b.onclick = e => { e.stopPropagation(); trocarPalavra(indice, s.tamanho, sug); };
    menu.appendChild(b);
  });
  const manter = document.createElement("button");
  manter.type = "button";
  manter.className = "manter";
  manter.textContent = "Manter";
  manter.style.setProperty("--i", Math.min(4, s.sugestoes.length));
  manter.onclick = e => { e.stopPropagation(); falado.mapa.delete(indice); fecharOpcoes(); desenharPalavras(false); };
  menu.appendChild(manter);
  envolve.appendChild(menu);
}

function ensinar(de, para) {
  if (sessao && sessao.token && de && para && SEM_ACENTO(M(de)) !== SEM_ACENTO(M(para))) chamar("/api/aprendizado/palavras", { de: de.slice(0, 40), para: para.slice(0, 40) }).catch(() => {});
}

function trocarPalavra(indice, tamanho, nova) {
  const palavras = $("descricao").value.split(" ").filter(Boolean);
  ensinar(palavras.slice(indice, indice + tamanho).join(" "), nova);
  palavras.splice(indice, tamanho, nova);
  const r = caber40(palavras.join(" "));
  $("descricao").value = r.texto;
  contarCaracteres();
  const novo = new Map();
  for (const [i, v] of falado.mapa) {
    if (i < indice) novo.set(i, v);
    else if (i >= indice + tamanho) novo.set(i - tamanho + 1, v);
  }
  falado.mapa = novo;
  document.querySelectorAll(".opcoes").forEach(o => o.remove());
  desenharPalavras(false);
  [...$("palavras").querySelectorAll(".palavra")].find(c => c.textContent === nova)?.classList.add("trocada");
  if (sessao) enviarFormulario($("consulta"));
}

async function prepararPalavras(f, outras, duvidas, confianca) {
  const palavras = f.texto.split(" ").filter(Boolean);
  const mapa = new Map();
  for (const d of (duvidas || await palavrasDuvidosas(f.texto))) juntarSugestao(mapa, d.indice, d.tamanho, d.sugestoes);
  for (const o of outras) {
    const alt = o.texto.split(" ").filter(Boolean);
    for (const [i0, i1, j0, j1] of diffPalavras(palavras, alt)) {
      if (i1 === i0 || j1 === j0) continue;
      if (i1 - i0 === j1 - j0) for (let k = 0; k < i1 - i0; k++) juntarSugestao(mapa, i0 + k, 1, [alt[j0 + k]]);
      else juntarSugestao(mapa, i0, i1 - i0, [alt.slice(j0, j1).join(" ")]);
    }
  }
  for (const [i, v] of mapa) {
    const agora = palavras.slice(i, i + v.tamanho).join(" ");
    v.sugestoes = v.sugestoes.filter(x => SEM_ACENTO(x).replace(/ /g, "") !== SEM_ACENTO(agora).replace(/ /g, ""));
    if (!v.sugestoes.length) mapa.delete(i);
  }
  falado = { mapa, confianca };
  const nota = [];
  if (f.abreviou) nota.push("abreviei para caber em 40");
  if (f.cortou) nota.push("passou de 40, confira o final");
  if (mapa.size) nota.push("palavras amarelas podem estar erradas: toque para trocar");
  else if (confianca && confianca < 0.6) nota.push("não tenho certeza, confira");
  $("falado-nota").textContent = nota.join(" · ");
  desenharPalavras();
}

let pedidoDeCriar = false;
prepararMic($("mic-descricao"), async (alternativas, final, confianca) => {
  if (!final) { usarFala(falaParaTexto(alternativas[0])); return; }
  const { melhor, duvidas, outras } = await escolherMelhor(alternativas);
  if (!melhor) return;
  usarFala(melhor);
  $("ultimo-ouvido").textContent = "Ouvi: \"" + alternativas[0] + "\" → " + melhor.texto + (melhor.unidade ? " · " + melhor.unidade : "");
  pedidoDeCriar = melhor.acao === "criar";
  await prepararPalavras(melhor, outras, duvidas, confianca);
  enviarFormulario($("consulta"));
});

document.addEventListener("click", fecharOpcoes);

prepararMic($("mic-busca"), (alternativas, final) => {
  const t = falaParaTexto(alternativas[0]).texto;
  $("busca").value = t;
  if (final && t.length >= 2) catalogo(t);
});

let lote = null;
let filtroLote = null;
const NOME_LINHA = { BLOQUEAR: "Já existe", REVISAR: "Parecido", LIBERAR: "Novo", REPETIDO: "Repetido", ERRO: "Com erro" };
const NOMES = { BLOQUEAR: "Já existem", REVISAR: "Parecidos", LIBERAR: "Novos", REPETIDO: "Repetidos", ERRO: "Com erro" };
const ACOES = { BLOQUEAR: [["USAR_EXISTENTE", "Usar o existente"], ["IGNORAR", "Ignorar"]],
                REVISAR: [["USAR_EXISTENTE", "Usar o existente"], ["CRIAR", "Criar código"], ["IGNORAR", "Ignorar"]],
                LIBERAR: [["CRIAR", "Criar código"], ["IGNORAR", "Ignorar"]],
                REPETIDO: [["CRIAR", "Mesmo da linha"], ["IGNORAR", "Ignorar"]],
                ERRO: [["IGNORAR", "Ignorar"]] };

function desenharLote() {
  const chips = $("lote-chips");
  chips.replaceChildren();
  const todas = document.createElement("button");
  todas.type = "button";
  todas.textContent = `Todas (${lote.linhas.length})`;
  todas.className = filtroLote ? "" : "ativa";
  todas.onclick = () => { filtroLote = null; desenharLote(); };
  chips.appendChild(todas);
  let ordem = 1;
  Object.entries(NOMES).forEach(([k, nome]) => {
    const n = lote.linhas.filter(l => l.resultado === k).length;
    if (!n) return;
    const b = document.createElement("button");
    b.type = "button";
    b.innerHTML = `<span class="res ${k}">●</span> `;
    b.append(`${nome} (${n})`);
    b.className = filtroLote === k ? "ativa" : "";
    b.style.setProperty("--i", ordem++);
    b.onclick = () => { filtroLote = k; desenharLote(); };
    chips.appendChild(b);
  });
  const visiveis = lote.linhas.filter(l => !filtroLote || l.resultado === filtroLote);
  const corpo = $("lote-linhas");
  corpo.replaceChildren();
  visiveis.slice(0, 300).forEach(l => {
    const tr = document.createElement("tr");
    celula(tr, l.linha).title = l.origem || "";
    [M(l.descricao), M(l.unidade), l.nome_padrao].forEach(v => celula(tr, v));
    celula(tr, NOME_LINHA[l.resultado] || l.resultado, "res " + l.resultado);
    const parecido = document.createElement("span");
    if (l.codigo_existente) parecido.append(botaoCodigo(l.codigo_existente), " " + M(l.descricao_existente));
    celula(tr, l.codigo_existente ? parecido : "");
    const sel = document.createElement("select");
    (ACOES[l.resultado] || ACOES.ERRO).forEach(([v, t]) => {
      const o = document.createElement("option");
      o.value = v;
      o.textContent = v === "CRIAR" && l.repete_linha ? `Mesmo da linha ${l.repete_linha}` : t;
      sel.appendChild(o);
    });
    sel.value = l.acao;
    sel.disabled = l.resultado === "ERRO" || !!l.situacao;
    sel.onchange = () => { l.acao = sel.value; };
    celula(tr, sel);
    celula(tr, l.codigo_final ? botaoCodigo(l.codigo_final) : "");
    celula(tr, l.situacao || l.motivo);
    corpo.appendChild(tr);
  });
  rotular(corpo);
  const importado = lote.linhas.some(l => l.situacao);
  $("lote-info").textContent = (visiveis.length > 300 ? `Mostrando 300 de ${visiveis.length}; o Excel traz todas. ` : "")
    + (importado ? "Importado. Baixe o Excel organizado." : "Dá para trocar a ação de cada linha antes de importar.");
  $("importar").disabled = importado;
  $("lote-resultado").classList.remove("oculto");
}

const comToken = (h = {}) => sessao && sessao.token ? Object.assign(h, { Authorization: "Bearer " + sessao.token }) : h;

async function baixar(url, corpo, nome) {
  rede(1);
  const r = await fetch(url, { method: "POST", headers: comToken({ "Content-Type": "application/json" }), body: JSON.stringify(corpo) }).finally(() => rede(-1));
  if (!r.ok) { const d = await r.json().catch(() => ({})); throw new Error(d.erro || "Erro " + r.status); }
  const a = document.createElement("a");
  a.href = URL.createObjectURL(await r.blob());
  a.download = (r.headers.get("Content-Disposition") || "").match(/filename="?([^"]+)"?/)?.[1] || nome;
  a.click();
  URL.revokeObjectURL(a.href);
}

$("form-lote").addEventListener("submit", async e => {
  e.preventDefault();
  const colado = $("lote-texto").value.trim();
  const arquivo = $("arquivo").files[0] || (colado ? new File([colado], "colado.txt", { type: "text/plain" }) : null);
  if (!arquivo) { aviso("Escolha uma planilha ou cole a lista.", "erro"); return; }
  const dados = new FormData();
  dados.append("arquivo", arquivo);
  dados.append("unidadePadrao", $("lote-unidade").value.trim());
  dados.append("grupoPadrao", $("lote-grupo").value);
  try { await pedirEntrada(); } catch (err) { aviso(err.message, "erro"); return; }
  $("analisar").classList.add("ocupado");
  aviso(`Analisando ${arquivo.name}…`, "");
  try {
    rede(1);
    const r = await fetch("/api/lotes/analisar", { method: "POST", headers: comToken(), body: dados }).finally(() => rede(-1));
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.erro || "Erro " + r.status);
    lote = d;
    filtroLote = null;
    const rel = $("lote-relatorio");
    rel.replaceChildren();
    const t = document.createElement("b");
    t.textContent = "Arrumei antes de analisar";
    const ul = document.createElement("ul");
    (d.relatorio || []).forEach(x => { const li = document.createElement("li"); li.textContent = x; ul.appendChild(li); });
    rel.append(t, ul);
    const s = d.resumo;
    aviso(`${s.TOTAL} linhas em ${(d.milissegundos / 1000).toFixed(1)} s: ${s.BLOQUEAR} já existem, ${s.REVISAR} parecidos, ${s.LIBERAR} novos, ${s.REPETIDO} repetidos, ${s.ERRO} com erro.`, "LIBERAR");
    desenharLote();
  } catch (err) { aviso(err.message, "erro"); }
  $("analisar").classList.remove("ocupado");
});

$("importar").addEventListener("click", async () => {
  if (!lote) return;
  try { await pedirEntrada(); } catch (e) { aviso(e.message, "erro"); return; }
  const criar = lote.linhas.filter(l => l.acao === "CRIAR").length;
  if (!confirm(`Importar ${lote.linhas.length} linhas em nome de ${sessao.nome}? ${criar} criam código.`)) return;
  $("importar").classList.add("ocupado");
  try {
    const r = await chamar("/api/lotes/importar", { linhas: lote.linhas });
    lote.linhas = r.linhas;
    const s = r.resumo;
    aviso(`Importado em ${(r.milissegundos / 1000).toFixed(1)} s: ${s.criados || 0} criados, ${s.usam_existente || 0} usam existente, ${s.nao_importadas || 0} fora.`, "LIBERAR");
    desenharLote();
    historico();
  } catch (err) { aviso(err.message, "erro"); }
  $("importar").classList.remove("ocupado");
});

$("exportar").addEventListener("click", async () => {
  if (!lote) return;
  try { await baixar("/api/lotes/exportar", { linhas: lote.linhas, relatorio: lote.relatorio || [] }, "codigos_organizados.xlsx"); }
  catch (err) { aviso(err.message, "erro"); }
});

async function checarVoz() {
  const itens = [];
  const serve = !!Reconhecedor && !navigator.brave;
  itens.push([serve ? "Navegador ok" : "Use Edge, Chrome ou Safari", serve]);
  itens.push([window.isSecureContext ? "Endereço ok" : "Abra por localhost:8080", window.isSecureContext]);
  itens.push([navigator.onLine ? "Internet ok" : "Sem internet", navigator.onLine]);
  itens.push([aceitaFiltro ? "Anti-ruído disponível" : "Anti-ruído indisponível neste navegador", aceitaFiltro ? true : null]);
  try {
    const p = await navigator.permissions.query({ name: "microphone" });
    itens.push([{ granted: "Microfone permitido", prompt: "O navegador vai pedir o microfone", denied: "Microfone bloqueado: libere no cadeado" }[p.state], p.state === "granted" ? true : p.state === "denied" ? false : null]);
  } catch (e) {}
  $("checagens").replaceChildren(...itens.map(([t, ok]) => { const s = document.createElement("span"); s.textContent = t; if (ok === true) s.className = "bom"; if (ok === false) s.className = "ruim"; return s; }));
}

async function testeVoz() {
  const mostrar = location.hash === "#testar-voz";
  $("teste-voz").classList.toggle("oculto", !mostrar);
  if (!mostrar) return;
  abrir("aba-consulta");
  $("teste-voz").classList.remove("oculto");
  checarVoz();
  if (!sessao) {
    try { definirSessao(await chamar("/api/acesso/entrar", { nome: "Teste de voz" }, { semToken: true })); $("janela-entrar").close(); carregar(); } catch (e) {}
  }
}
window.addEventListener("hashchange", testeVoz);
$("fechar-teste").addEventListener("click", () => { history.replaceState(null, "", location.pathname); testeVoz(); });

async function iniciar() {
  const s = guardado.sessao();
  if (s && s.token) {
    sessao = s;
    try { definirSessao(Object.assign({}, s, await chamar("/api/acesso/eu", null, { silencioso: true }))); } catch (e) { if (!semConexao) definirSessao(null); }
  }
  definirSessao(sessao);
  verBanco();
  const destino = { "#catalogo": "aba-catalogo", "#planilha": "aba-lote", "#curador": "aba-curador", "#unidades": "aba-unidades", "#raio-x": "aba-raio-x" }[location.hash];
  if (destino) abrir(destino);
  prepararModoLocal();
  if (sessao) carregar();
  else if (location.hash !== "#testar-voz") pedirEntrada().catch(() => {});
  testeVoz();
  moverIndicador();
}
addEventListener("DOMContentLoaded", iniciar);

const caixaArquivo = $("caixa-arquivo");
$("arquivo").addEventListener("change", () => {
  const f = $("arquivo").files[0];
  $("arquivo-nome").textContent = f ? f.name : "ou arraste a planilha aqui";
  caixaArquivo.classList.toggle("escolhido", !!f);
});
["dragenter", "dragover"].forEach(t => caixaArquivo.addEventListener(t, () => caixaArquivo.classList.add("arrastando")));
["dragleave", "drop"].forEach(t => caixaArquivo.addEventListener(t, () => caixaArquivo.classList.remove("arrastando")));
