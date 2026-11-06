const LARGURA = 640, ALTURA = 400;
const numero = n => Math.round(n).toLocaleString("pt-BR");

const CENAS = {
  consulta: [
    ["legenda", "1", "Fale ou digite o serviço"], ["esperar", 500], ["clicar", "mic"], ["zoom", "campo", 1.32], ["fase", "ouvindo"],
    ["digitar", 55], ["esperar", 300], ["fase", "corrigido"], ["marca", "digitado"], ["esperar", 700], ["zoom"],
    ["legenda", "2", "O MOKKA confere na hora"], ["clicar", "consultar"], ["fase", "resultado"], ["marca", "resultado"],
    ["esperar", 250], ["zoom", ".faixa b", 1.32], ["esperar", 1700], ["zoom"], ["legenda", "3", "Os 5 mais parecidos, e mais ao lado"],
    ["esperar", 700], ["zoom", ".pager", 1.45], ["clicar", "proximo"], ["fase", "pagina2"], ["esperar", 900], ["zoom"],
    ["esperar", 900], ["legenda", "4", "Usa o código que já existe"], ["clicar", "usar"], ["fase", "pronto"], ["zoom", ".pronto", 1.3],
    ["esperar", 2000], ["zoom"], ["legenda"], ["esperar", 700]
  ],
  curador: [
    ["legenda", "1", "Pedidos iguais chegam juntos"], ["esperar", 400], ["zoom", ".caso-topo > span:nth-child(2)", 1.32], ["fase", "f1"], ["esperar", 1500],
    ["legenda", "2", "O MOKKA mostra o que muda"], ["zoom", ".comparar div:first-child", 1.32], ["esperar", 1300], ["zoom", ".comparar div:last-child", 1.32], ["esperar", 1400], ["fase", "f2"],
    ["legenda", "3", "E sugere a decisão, com a certeza"], ["zoom", ".sugestao b", 1.32], ["marca", "sugestao"], ["esperar", 1700],
    ["legenda", "4", "O curador aprova o grupo de uma vez"], ["zoom", ".caso-botoes", 1.32], ["clicar", "aprovar"], ["fase", "aprovado"], ["esperar", 300], ["zoom", "caso", 1],
    ["esperar", 1100], ["zoom"], ["fase", "saiu"], ["contar", 900], ["legenda", "5", "E o MOKKA aprende a regra"],
    ["esperar", 2200], ["legenda"], ["esperar", 600]
  ],
  raiox: [
    ["legenda", "1", "A nota de saúde do catálogo"], ["esperar", 400], ["zoom", ".nota", 1.32], ["esperar", 500], ["fase", "f1"],
    ["contar", 1700], ["esperar", 600], ["legenda", "2", "Onde estão os repetidos"], ["zoom", ".barras", 1.32], ["fase", "f2"],
    ["esperar", 1600], ["legenda", "3", "E as maiores famílias"], ["zoom", ".familias", 1.32], ["fase", "f3"], ["esperar", 1900],
    ["zoom"], ["legenda", "4", "Base original 77, catálogo tratado 100"], ["esperar", 2000], ["legenda"], ["esperar", 500]
  ],
  planilha: [
    ["legenda", "1", "Arraste a planilha inteira"], ["esperar", 400], ["fase", "arrastando"], ["esperar", 1100], ["fase", "solto"],
    ["zoom", ".soltar-nome", 1.32], ["esperar", 1500], ["zoom"], ["fase", "linhas"], ["legenda", "2", "Cada linha volta com o que fazer"],
    ["esperar", 400], ["zoom", ".chips", 1.32], ["contar", 1300], ["marca", "analisado"], ["esperar", 600], ["zoom", ".tabela .linha:nth-child(3)", 1.32],
    ["esperar", 1500], ["zoom"], ["legenda", "3", "E o Excel sai pronto para o SAP"], ["clicar", "baixar"], ["fase", "baixado"],
    ["zoom", ".baixar", 1.32], ["esperar", 1800], ["zoom"], ["legenda"], ["esperar", 600]
  ]
};

function duracao(cena) {
  return cena.reduce((t, [tipo, v]) => t + (tipo === "esperar" || tipo === "contar" ? v : tipo === "clicar" ? 1000 : tipo === "zoom" ? 700 : tipo === "digitar" ? 20 * v : 0), 0);
}

function janela(endereco) {
  const j = document.createElement("div");
  j.className = "janela";
  j.innerHTML = `<div class="janela-barra"><i></i><i></i><i></i><span class="endereco"><svg><use href="#i-cadeado"/></svg>${endereco}</span></div>`
    + '<div class="palco-caixa"><div class="palco"></div></div>';
  return j;
}

function escalar(caixa) {
  const palco = caixa.querySelector(".palco");
  const ajustar = () => {
    const e = caixa.clientWidth / LARGURA;
    palco.style.transform = `scale(${e})`;
    caixa.style.height = ALTURA * e + "px";
    caixa.dataset.escala = e;
  };
  new ResizeObserver(ajustar).observe(caixa);
  ajustar();
}

class Filme {
  constructor(palco, nome) {
    this.palco = palco;
    this.nome = nome;
    this.cena = CENAS[nome];
    this.total = duracao(this.cena);
    this.tocando = false;
    this.geracao = 0;
    this.decorrido = 0;
    this.aoAndar = () => {};
  }

  montar() {
    this.camera = document.createElement("div");
    this.camera.className = "camera";
    this.tela = document.getElementById("tela-" + this.nome).content.firstElementChild.cloneNode(true);
    this.cursor = document.createElement("span");
    this.cursor.className = "cursor";
    this.cursor.style.transform = `translate(${LARGURA * .78}px, ${ALTURA + 40}px)`;
    this.camera.append(this.tela, this.cursor);
    this.palco.replaceChildren(this.camera);
    if (this.legenda) this.dizer();
    this.decorrido = 0;
    this.aoAndar(0);
  }

  achar(nome) {
    return /^[.\[]/.test(nome) ? this.tela.querySelector(nome) : this.alvo(nome);
  }

  pos(el) {
    const t = this.tela.getBoundingClientRect(), a = el.getBoundingClientRect(), k = t.width / LARGURA || 1;
    return { x: (a.left - t.left) / k, y: (a.top - t.top) / k, w: a.width / k, h: a.height / k };
  }

  zoom(nome, s) {
    const el = nome && !this.instantaneo && this.achar(nome);
    if (!el) { this.camera.style.transform = ""; return; }
    const { x, y, w, h } = this.pos(el);
    s = Math.max(1, Math.min(s, (LARGURA - 40) / w, (ALTURA - 40) / h));
    const tx = Math.min(0, Math.max(LARGURA - LARGURA * s, LARGURA / 2 - (x + w / 2) * s));
    const ty = Math.min(0, Math.max(ALTURA - ALTURA * s, ALTURA / 2 - (y + h / 2) * s));
    this.camera.style.transform = `translate(${tx}px, ${ty}px) scale(${s})`;
  }

  dizer(n, texto) {
    const l = this.legenda;
    if (!l || this.instantaneo) return;
    if (!texto) { l.classList.remove("mostra"); return; }
    l.classList.remove("mostra");
    void l.offsetWidth;
    l.innerHTML = "";
    const b = document.createElement("b");
    b.textContent = n;
    l.append(b, texto);
    l.classList.add("mostra");
  }

  espera(ms, g) {
    if (this.instantaneo) return Promise.resolve();
    return new Promise(ok => {
      let resta = ms, antes = performance.now();
      const passo = agora => {
        if (g !== this.geracao) return ok();
        if (this.tocando) { resta -= agora - antes; this.decorrido += agora - antes; this.aoAndar(Math.min(1, this.decorrido / this.total)); }
        antes = agora;
        resta <= 0 ? ok() : requestAnimationFrame(passo);
      };
      requestAnimationFrame(passo);
    });
  }

  alvo(nome) {
    return this.tela.querySelector(`[data-alvo="${nome}"]`);
  }

  async mover(nome, g) {
    const el = this.alvo(nome);
    if (!el || this.instantaneo) return;
    const { x: ex, y: ey, w, h } = this.pos(el);
    const x = ex + w / 2, y = ey + h / 2;
    this.cursor.classList.add("visivel");
    this.cursor.style.transform = `translate(${x}px, ${y}px)`;
    await this.espera(650, g);
  }

  async clicar(nome, g) {
    await this.mover(nome, g);
    const el = this.alvo(nome);
    if (this.instantaneo || !el) return;
    this.cursor.classList.remove("clica");
    void this.cursor.offsetWidth;
    this.cursor.classList.add("clica");
    el.classList.add("apertado");
    await this.espera(220, g);
    el.classList.remove("apertado");
    await this.espera(130, g);
  }

  async digitar(ms, g) {
    const el = this.tela.querySelector("[data-digitar]"), tam = this.tela.querySelector("[data-tamanho]");
    const texto = el.dataset.digitar;
    for (let i = 1; i <= texto.length; i++) {
      if (g !== this.geracao) return;
      if (!this.instantaneo || i === texto.length) { el.textContent = texto.slice(0, i); tam.textContent = i; }
      await this.espera(ms, g);
    }
  }

  async contar(ms, g) {
    const els = [...this.tela.querySelectorAll("[data-contar-cena]")].filter(el => !el.dataset.contado);
    const passos = this.instantaneo ? 1 : Math.max(1, Math.round(ms / 40));
    for (let k = 1; k <= passos; k++) {
      if (g !== this.geracao) return;
      const f = 1 - Math.pow(1 - k / passos, 3);
      els.forEach(el => { el.textContent = numero(+el.dataset.de + (el.dataset.ate - el.dataset.de) * f); });
      await this.espera(ms / passos, g);
    }
    els.forEach(el => { el.dataset.contado = 1; });
  }

  async passo([tipo, v, w], g) {
    if (tipo === "esperar") return this.espera(v, g);
    if (tipo === "zoom") { this.zoom(v, w); return this.espera(700, g); }
    if (tipo === "legenda") return this.dizer(v, w);
    if (tipo === "fase") {
      this.tela.classList.add("e-" + v);
      if (v === "corrigido") { const d = this.tela.querySelector("[data-digitar]"); d.textContent = d.dataset.digitar.toUpperCase(); }
      return;
    }
    if (tipo === "clicar") return this.clicar(v, g);
    if (tipo === "digitar") return this.digitar(v, g);
    if (tipo === "contar") return this.contar(v, g);
  }

  async ate(marca) {
    const g = ++this.geracao;
    this.tocando = false;
    this.instantaneo = true;
    this.montar();
    for (const p of this.cena) {
      if (p[0] === "marca" && p[1] === marca) break;
      await this.passo(p, g);
    }
    this.instantaneo = false;
    this.cursor.classList.remove("visivel");
  }

  async rodar() {
    const g = ++this.geracao;
    this.tocando = true;
    let primeira = true;
    while (g === this.geracao) {
      if (!primeira) { this.palco.classList.add("trocando"); await this.espera(450, g); }
      if (g !== this.geracao) return;
      this.montar();
      primeira = false;
      requestAnimationFrame(() => this.palco.classList.remove("trocando"));
      for (const p of this.cena) {
        if (g !== this.geracao) return;
        await this.passo(p, g);
      }
    }
  }

  pausar() { this.tocando = false; this.palco.classList.add("pausado"); }
}

document.querySelectorAll("figure.video").forEach(fig => {
  const j = janela("mokka · " + fig.dataset.rotulo.toLowerCase());
  fig.prepend(j);
  const caixa = j.querySelector(".palco-caixa");
  escalar(caixa);
  const controles = document.createElement("div");
  controles.className = "controles";
  controles.innerHTML = '<button type="button" class="tocar" aria-label="Pausar"><svg><use href="#i-pausa"/></svg></button>'
    + `<span class="legenda" aria-live="off"></span><span class="rotulo">${fig.dataset.rotulo}</span><span class="tempo"><i></i></span>`;
  j.appendChild(controles);
  const filme = new Filme(caixa.querySelector(".palco"), fig.dataset.demo);
  filme.legenda = controles.querySelector(".legenda");
  const barra = controles.querySelector(".tempo i"), botao = controles.querySelector(".tocar");
  filme.aoAndar = f => { barra.style.transform = `scaleX(${f})`; };
  let comecou = false, pausadoPeloUsuario = false;
  const marcar = tocando => {
    botao.querySelector("use").setAttribute("href", tocando ? "#i-pausa" : "#i-play");
    botao.setAttribute("aria-label", tocando ? "Pausar" : "Tocar");
    fig.classList.toggle("parado", !tocando);
  };
  const tocar = () => {
    if (!comecou) { comecou = true; filme.rodar(); } else { filme.tocando = true; filme.palco.classList.remove("pausado"); }
    marcar(true);
  };
  const pausar = () => { filme.pausar(); marcar(false); };
  botao.addEventListener("click", () => {
    if (filme.tocando) { pausadoPeloUsuario = true; pausar(); } else { pausadoPeloUsuario = false; tocar(); }
  });
  caixa.addEventListener("click", () => botao.click());
  filme.ate(null);
  new IntersectionObserver(([e]) => {
    if (e.isIntersecting && !pausadoPeloUsuario) tocar();
    else if (!e.isIntersecting && filme.tocando) pausar();
  }, { threshold: .35 }).observe(fig);
});

const PASSOS = [
  { tela: "consulta", ate: "digitado", alvo: '[data-alvo="campo"]', titulo: "Digite ou fale o serviço",
    texto: "Escreva do seu jeito ou aperte o microfone. O MOKKA corrige a escrita e abrevia para caber nos 40 caracteres do SAP." },
  { tela: "consulta", ate: "resultado", alvo: ".faixa", titulo: "A resposta na hora",
    texto: "Vermelho: já existe. Amarelo: existe parecido. Verde: é novo e pode criar. Tudo em menos de um segundo." },
  { tela: "consulta", ate: "resultado", alvo: ".pager", titulo: "Os 5 mais parecidos primeiro",
    texto: "A lista começa pelos 5 mais parecidos. Se precisar, use as setas para ir vendo os próximos." },
  { tela: "curador", ate: "sugestao", alvo: '[data-alvo="caso"]', titulo: "O curador decide os difíceis",
    texto: "Quem insiste em criar um código parecido vai para o curador. Pedidos iguais chegam juntos, com a sugestão e a certeza do MOKKA." },
  { tela: "curador", ate: null, alvo: ".aprendi", titulo: "O MOKKA aprende",
    texto: "Cada decisão vira regra. A próxima consulta igual já vem com o código certo, e dá para desfazer." },
  { tela: "planilha", ate: "analisado", alvo: ".chips", titulo: "A planilha inteira de uma vez",
    texto: "Arraste até 100.000 linhas em Excel, CSV, texto ou JSON. Cada linha volta com o que fazer, e o Excel sai pronto para o SAP." },
  { tela: "raiox", ate: null, alvo: ".nota", titulo: "Raio-X do catálogo",
    texto: "A saúde da base em uma nota de 0 a 100, antes e depois, com as famílias de repetidos e os números por grupo." }
];

(() => {
  const caixa = document.querySelector("#tour .palco-caixa");
  if (!caixa) return;
  escalar(caixa);
  const palco = $id("tour-palco");
  const foco = document.createElement("span");
  foco.className = "foco";
  const filmes = {};
  let atual = 0;
  const pontos = $id("tour-pontos");
  PASSOS.forEach(() => pontos.appendChild(document.createElement("i")));

  async function mostrar(i) {
    atual = i;
    const p = PASSOS[i];
    if (!filmes[p.tela]) filmes[p.tela] = new Filme(palco, p.tela);
    const f = filmes[p.tela];
    f.palco = palco;
    await f.ate(p.ate);
    palco.appendChild(foco);
    const el = f.tela.querySelector(p.alvo);
    const e = parseFloat(caixa.dataset.escala) || 1;
    const a = el.getBoundingClientRect(), b = palco.getBoundingClientRect();
    foco.style.cssText = `left:${(a.left - b.left) / e - 6}px;top:${(a.top - b.top) / e - 6}px;width:${a.width / e + 12}px;height:${a.height / e + 12}px`;
    $id("tour-passo").textContent = `${i + 1} de ${PASSOS.length}`;
    $id("tour-titulo").textContent = p.titulo;
    $id("tour-texto").textContent = p.texto;
    $id("tour-voltar").disabled = i === 0;
    const ultimo = i === PASSOS.length - 1;
    $id("tour-proximo").textContent = ultimo ? "Ver de novo" : "Próximo";
    [...pontos.children].forEach((d, k) => d.classList.toggle("ativo", k === i));
    const balao = $id("tour-balao");
    balao.classList.remove("troca");
    void balao.offsetWidth;
    balao.classList.add("troca");
  }

  $id("tour-voltar").addEventListener("click", () => atual > 0 && mostrar(atual - 1));
  $id("tour-proximo").addEventListener("click", () => {
    mostrar(atual === PASSOS.length - 1 ? 0 : atual + 1);
  });
  new ResizeObserver(() => mostrar(atual)).observe(caixa);
})();

function $id(id) { return document.getElementById(id); }

const contador = new IntersectionObserver((itens, obs) => {
  itens.forEach(e => {
    if (!e.isIntersecting) return;
    obs.unobserve(e.target);
    const fim = +e.target.dataset.contar, ini = performance.now(), ms = 1400;
    const passo = agora => {
      const f = ms ? Math.min(1, (agora - ini) / ms) : 1;
      e.target.textContent = numero(fim * (1 - Math.pow(1 - f, 3)));
      if (f < 1) requestAnimationFrame(passo);
    };
    requestAnimationFrame(passo);
  });
}, { threshold: .6 });
document.querySelectorAll("[data-contar]").forEach(el => contador.observe(el));

{
  const revelador = new IntersectionObserver((itens, obs) => {
    itens.forEach(e => { if (e.isIntersecting) { e.target.classList.add("visto"); obs.unobserve(e.target); } });
  }, { threshold: .15, rootMargin: "0px 0px -40px 0px" });
  document.querySelectorAll(".recurso, .bloco-texto, .bloco .video, .grade.quatro article, .titulo-secao, .sub-secao, .tour").forEach(el => {
    const irmaos = [...el.parentElement.children].filter(x => x.matches(el.tagName.toLowerCase()));
    el.style.setProperty("--d", Math.min(irmaos.indexOf(el), 7) * 70 + "ms");
    el.classList.add("revelar");
    revelador.observe(el);
  });
}

const topo = $id("topo");
addEventListener("scroll", () => topo.classList.toggle("rolou", scrollY > 8), { passive: true });
