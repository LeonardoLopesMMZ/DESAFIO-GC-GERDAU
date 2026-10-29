const CORES_GRUPO = { "Civil": "#003366", "Facilities": "#0b74b8", "Eletromecânica": "#ffcc00" };
const dinheiro = v => v.toLocaleString("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 });
let raioX = null;

function medidor(id, nota) {
  const caixa = $(id);
  caixa.querySelector(".arco").style.strokeDashoffset = 100 - nota;
  contar(caixa.querySelector("b"), nota);
  caixa.classList.toggle("boa", nota >= 90);
  caixa.classList.toggle("media", nota >= 70 && nota < 90);
}

function calcularEconomia() {
  if (!raioX) return;
  const custo = Number($("custo").value);
  const total = raioX.repetidos_achados + raioX.duplicados_evitados;
  $("custo-valor").textContent = dinheiro(custo);
  $("economia-valor").textContent = dinheiro(total * custo);
  $("economia-texto").textContent = `${raioX.repetidos_achados.toLocaleString("pt-BR")} códigos repetidos achados na base`
    + (raioX.duplicados_evitados ? ` + ${raioX.duplicados_evitados} duplicados evitados agora` : "")
    + `, cada um a ${dinheiro(custo)}.`;
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
    calcularEconomia();
  } catch (e) { aviso(e.message, "erro"); }
}

$("custo").addEventListener("input", calcularEconomia);
