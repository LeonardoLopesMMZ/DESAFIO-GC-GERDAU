const CORES_GRUPO = { "Civil": "#003366", "Facilities": "#0b74b8", "Eletromecânica": "#ffcc00" };
let raioX = null;

function medidor(id, nota) {
  const caixa = $(id);
  caixa.querySelector(".arco").style.strokeDashoffset = 100 - nota;
  contar(caixa.querySelector("b"), nota);
  caixa.classList.toggle("boa", nota >= 90);
  caixa.classList.toggle("media", nota >= 70 && nota < 90);
}

function mostrarGanho(r) {
  const base = r.codigos_na_base, rep = r.repetidos_achados;
  contar($("ganho-lista"), base ? Math.round(100 * rep / base) : 0);
  contar($("ganho-repetidos"), rep);
  $("ganho-antes").textContent = rep ? `1 em ${Math.round(base / rep)}` : "0";
  $("ganho-antes-texto").textContent = rep ? "códigos da base era repetido; agora nenhum código novo nasce repetido" : "códigos repetidos na base";
  contar($("ganho-evitados"), r.duplicados_evitados);
  contar($("ganho-aprendeu"), r.decisoes_aprendidas);
}

function desenharCriterios(lista) {
  $("criterios").replaceChildren(...lista.map((c, i) => {
    const linha = document.createElement("div");
    linha.className = "criterio";
    linha.style.setProperty("--i", i);
    linha.innerHTML = `<span class="nome"></span><span class="peso"></span>
      <span class="barra antes"><i></i><em></em></span><span class="barra depois"><i></i><em></em></span>`;
    linha.querySelector(".nome").textContent = c.nome;
    linha.querySelector(".peso").textContent = "peso " + c.peso;
    [["antes", c.antes], ["depois", c.depois]].forEach(([k, v]) => {
      const b = linha.querySelector("." + k);
      b.querySelector("em").textContent = `${k === "antes" ? "antes" : "depois"} ${v}%`;
      requestAnimationFrame(() => requestAnimationFrame(() => { b.querySelector("i").style.width = v + "%"; }));
    });
    return linha;
  }));
}

function empacotar(familias, largura, altura) {
  const maior = Math.max(...familias.map(f => f.repetidos));
  const raio = n => 10 + 34 * Math.sqrt(n / maior);
  const postos = [];
  for (const f of familias) {
    const r = raio(f.repetidos);
    let ok = null;
    for (let passo = 0; passo < 4000 && !ok; passo++) {
      const ang = passo * 0.35, dist = passo * 0.9;
      const x = largura / 2 + Math.cos(ang) * dist * 1.6, y = altura / 2 + Math.sin(ang) * dist;
      if (x - r < 4 || x + r > largura - 4 || y - r < 4 || y + r > altura - 4) continue;
      if (postos.every(p => Math.hypot(p.x - x, p.y - y) >= p.r + r + 3)) ok = { x, y, r, f };
    }
    if (ok) postos.push(ok);
  }
  return postos;
}

function desenharConstelacao(familias) {
  const svg = $("constelacao"), L = 900, A = 440, NS = "http://www.w3.org/2000/svg";
  svg.setAttribute("viewBox", `0 0 ${L} ${A}`);
  svg.replaceChildren();
  const dica = $("dica-bolha");
  empacotar(familias, L, A).forEach((p, i) => {
    const g = document.createElementNS(NS, "g");
    g.setAttribute("class", "bolha-familia");
    g.style.setProperty("--i", i);
    g.style.transformOrigin = `${p.x}px ${p.y}px`;
    const c = document.createElementNS(NS, "circle");
    c.setAttribute("cx", p.x); c.setAttribute("cy", p.y); c.setAttribute("r", p.r);
    c.setAttribute("fill", CORES_GRUPO[p.f.grupo] || "#526579");
    g.appendChild(c);
    if (p.r > 22) {
      const t = document.createElementNS(NS, "text");
      t.setAttribute("x", p.x); t.setAttribute("y", p.y + 4);
      t.setAttribute("class", p.f.grupo === "Eletromecânica" ? "escuro" : "");
      t.textContent = "+" + p.f.repetidos;
      g.appendChild(t);
    }
    g.addEventListener("mouseenter", () => {
      dica.textContent = `${p.f.codigo} · ${M(p.f.descricao)} [${p.f.unidade}] · ${p.f.repetidos} códigos repetidos apontavam para ele`;
      dica.classList.add("ativa");
    });
    g.addEventListener("mouseleave", () => dica.classList.remove("ativa"));
    g.addEventListener("click", () => ficha(p.f.codigo));
    svg.appendChild(g);
  });
  $("legenda").replaceChildren(...Object.entries(CORES_GRUPO).map(([g, cor]) => {
    const s = document.createElement("span");
    s.innerHTML = `<i style="background:${cor}"></i>`;
    s.append(g);
    return s;
  }));
}

function desenharGrupos(grupos) {
  $("grupos-raio").replaceChildren(...grupos.map(g => {
    const d = document.createElement("div");
    d.className = "grupo-raio";
    const pct = Math.round(100 * g.repetidos / g.codigos);
    d.innerHTML = `<b></b><span></span><span class="barra"><i></i></span>`;
    d.querySelector("b").textContent = g.grupo;
    d.querySelector("span").textContent = `${g.repetidos.toLocaleString("pt-BR")} repetidos em ${g.codigos.toLocaleString("pt-BR")} códigos (${pct}%)`;
    d.querySelector(".barra i").style.background = CORES_GRUPO[g.grupo] || "#526579";
    requestAnimationFrame(() => requestAnimationFrame(() => { d.querySelector(".barra i").style.width = Math.min(100, pct * 4) + "%"; }));
    return d;
  }));
}

async function carregarRaioX() {
  try {
    raioX = await chamar("/api/raio-x");
    medidor("nota-antes", raioX.nota_antes);
    setTimeout(() => medidor("nota-depois", raioX.nota_depois), 450);
    desenharCriterios(raioX.criterios);
    desenharConstelacao(raioX.familias);
    desenharGrupos(raioX.grupos);
    mostrarGanho(raioX);
  } catch (e) { aviso(e.message, "erro"); }
}


const NOMES_RESULTADO = { BLOQUEAR: "Já existe", REVISAR: "Parecido", LIBERAR: "Novo" };
const NOMES_STATUS = { USOU_EXISTENTE: "Usou o que já existia", CRIADO: "Criou código novo", AGUARDANDO_CURADOR: "Foi para o curador", RECUSADO: "Curador recusou", PENDENTE: "Só consultou" };
const NOMES_ENTRADA = { ROSTO: "Pelo rosto", CHAVE: "Chave do Windows", NOME: "Pelo nome", LOCAL: "Teste automático" };
let relogioPainel = null;

function barrasPainel(id, mapa, nomes) {
  const total = Object.values(mapa).reduce((a, b) => a + b, 0) || 1;
  const itens = Object.entries(mapa).sort((a, b) => b[1] - a[1]);
  $(id).replaceChildren(...(itens.length ? itens : [["", 0]]).map(([k, n], i) => {
    const d = document.createElement("div");
    d.className = "barra-painel " + k;
    d.style.setProperty("--i", i);
    d.innerHTML = "<span></span><b></b><span class=\"trilho\"><i></i></span>";
    d.querySelector("span").textContent = k ? (nomes[k] || k) : "Ainda sem dados";
    d.querySelector("b").textContent = n.toLocaleString("pt-BR");
    requestAnimationFrame(() => requestAnimationFrame(() => { d.querySelector("i").style.width = (100 * n / total) + "%"; }));
    return d;
  }));
}

function rosca(mapa) {
  const NS = "http://www.w3.org/2000/svg", svg = $("p-rosca");
  const ordem = ["BLOQUEAR", "REVISAR", "LIBERAR"], total = ordem.reduce((a, k) => a + (mapa[k] || 0), 0);
  svg.replaceChildren();
  const fundo = document.createElementNS(NS, "circle");
  Object.entries({ cx: 21, cy: 21, r: 15.9, class: "fundo" }).forEach(([k, v]) => fundo.setAttribute(k, v));
  svg.appendChild(fundo);
  let feito = 0;
  ordem.forEach((k, i) => {
    const n = mapa[k] || 0;
    if (!n) return;
    const parte = 100 * n / total, c = document.createElementNS(NS, "circle");
    Object.entries({ cx: 21, cy: 21, r: 15.9, pathLength: 100, class: "fatia " + k }).forEach(([a, v]) => c.setAttribute(a, v));
    c.style.setProperty("--parte", Math.max(0, parte - 1.2));
    c.style.setProperty("--inicio", -feito);
    c.style.setProperty("--i", i);
    svg.appendChild(c);
    feito += parte;
  });
  contar($("p-rosca-total"), total);
  $("p-rosca-legenda").replaceChildren(...ordem.map(k => {
    const s = document.createElement("span");
    const n = mapa[k] || 0;
    s.innerHTML = `<i class="${k}"></i>`;
    s.append(`${NOMES_RESULTADO[k]}: ${n.toLocaleString("pt-BR")} (${total ? Math.round(100 * n / total) : 0}%)`);
    return s;
  }));
}

function colunasDias(dias) {
  const maior = Math.max(1, ...dias.map(d => d.bloquear + d.revisar + d.liberar));
  $("p-dias").replaceChildren(...dias.map((d, i) => {
    const col = document.createElement("div");
    col.className = "coluna-dia";
    col.style.setProperty("--i", i);
    const total = d.bloquear + d.revisar + d.liberar;
    const data = new Date(d.dia + "T12:00:00");
    col.title = `${data.toLocaleDateString("pt-BR")}: ${total} consultas (${d.bloquear} já existiam, ${d.revisar} parecidas, ${d.liberar} novas)`;
    col.innerHTML = `<span class="pilha" style="--h:${100 * total / maior}%"><i class="LIBERAR" style="flex:${d.liberar}"></i><i class="REVISAR" style="flex:${d.revisar}"></i><i class="BLOQUEAR" style="flex:${d.bloquear}"></i></span><b>${total || ""}</b><small>${data.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })}</small>`;
    return col;
  }));
}

async function carregarPainel() {
  try {
    const p = await chamar("/api/raio-x/painel");
    const st = p.por_status, decididas = (st.USOU_EXISTENTE || 0) + (st.CRIADO || 0) + (st.RECUSADO || 0);
    contar($("p-consultas"), p.consultas);
    contar($("p-hoje"), p.consultas_hoje);
    contar($("p-barrados"), (st.USOU_EXISTENTE || 0) + (st.RECUSADO || 0));
    contar($("p-criados"), p.codigos_criados);
    contar($("p-importados"), p.codigos_importados);
    contar($("p-reuso"), decididas ? Math.round(100 * ((st.USOU_EXISTENTE || 0) + (st.RECUSADO || 0)) / decididas) : 0);
    contar($("p-curador"), st.AGUARDANDO_CURADOR || 0);
    contar($("p-regras"), p.regras_aprendidas);
    contar($("p-votos"), p.votos);
    contar($("p-pessoas"), p.pessoas);
    colunasDias(p.por_dia);
    rosca(p.por_resultado);
    barrasPainel("p-status", st, NOMES_STATUS);
    barrasPainel("p-entrada", p.por_entrada, NOMES_ENTRADA);
    $("p-top").replaceChildren(...(p.mais_consultados.length ? p.mais_consultados : [{ descricao: "Ainda sem consultas", vezes: 0 }]).map((c, i) => {
      const li = document.createElement("li");
      li.style.setProperty("--i", i);
      li.innerHTML = "<span></span><b></b>";
      li.querySelector("span").textContent = c.descricao;
      li.querySelector("b").textContent = c.vezes ? c.vezes + "×" : "";
      return li;
    }));
    $("p-feed").replaceChildren(...(p.recentes.length ? p.recentes : [{ descricao: "Nenhuma consulta ainda", resultado: "", quem: "", quando: "" }]).map((r, i) => {
      const li = document.createElement("li");
      li.style.setProperty("--i", i);
      li.innerHTML = `<i class="${r.resultado || ""}"></i><span><b></b><small></small></span>`;
      li.querySelector("b").textContent = r.unidade ? `${r.descricao} [${r.unidade}]` : r.descricao;
      li.querySelector("small").textContent = [r.quem, NOMES_RESULTADO[r.resultado], NOMES_STATUS[r.status], r.quando ? quando(r.quando) : ""].filter(Boolean).join(" · ");
      return li;
    }));
    $("painel-hora").lastChild.textContent = "ao vivo · " + new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  } catch (e) { aviso(e.message, "erro"); }
  clearTimeout(relogioPainel);
  relogioPainel = setTimeout(() => { if (!$("aba-raio-x").classList.contains("oculto") && !document.hidden) carregarPainel(); }, 15000);
}
