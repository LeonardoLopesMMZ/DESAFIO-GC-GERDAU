const NUMEROS = {
  zero: 0, um: 1, uma: 1, dois: 2, duas: 2, tres: 3, quatro: 4, cinco: 5, seis: 6, sete: 7, oito: 8, nove: 9,
  dez: 10, onze: 11, doze: 12, treze: 13, catorze: 14, quatorze: 14, quinze: 15, dezesseis: 16, dezessete: 17,
  dezoito: 18, dezenove: 19, vinte: 20, trinta: 30, quarenta: 40, cinquenta: 50, cincoenta: 50, sessenta: 60,
  setenta: 70, oitenta: 80, noventa: 90, cem: 100, cento: 100, duzentos: 200, trezentos: 300, quatrocentos: 400,
  quinhentos: 500, seiscentos: 600, setecentos: 700, oitocentos: 800, novecentos: 900, mil: 1000
};

const TROCAS = [
  [/\bbarra\b/g, "/"], [/\b(tra[cç]o|h[ií]fen)\b/g, "-"],
  [/\bmil[ií]metros? quadrados?\b/g, "mm2"], [/\bmil[ií]metros?\b/g, "mm"], [/\bcent[ií]metros?\b/g, "cm"], [/\bpolegadas?\b/g, "pol"],
  [/\bmetros? quadrados?\b/g, "m2"], [/\bmetros? c[uú]bicos?\b/g, "m3"],
  [/\bdomingos? e feriados?\b/g, "dom/fer"], [/\bsegunda a s[aá]bado\b/g, "seg a sab"],
  [/\bpara\b/g, "p/"], [/\bcom\b/g, "c/"]
];

const FRASES = [
  ["TECNICO DE SEGURANCA DO TRABALHO", "TST"], ["TECNICO EM SEGURANCA DO TRABALHO", "TST"], ["TECNICO SEGURANCA TRABALHO", "TST"],
  ["AUXILIAR DE SERVICOS GERAIS", "ASG"], ["AUXILIAR SERVICOS GERAIS", "ASG"], ["ADICIONAL NOTURNO", "AD NOT"],
  ["HORA EXTRA", "HE"], ["HORA NORMAL", "HN"], ["SEGUNDA A SABADO", "SEG A SAB"], ["DOMINGO E FERIADO", "DOM/FER"],
  ["DOMINGOS E FERIADOS", "DOM/FER"], ["DOMINGO FERIADO", "DOM/FER"]
];
const PALAVRAS = {
  PERICULOSIDADE: "PERIC", INSALUBRIDADE: "INSAL", ESPECIALIZADO: "ESPEC", ESPECIALIZADA: "ESPEC", RECEPCIONISTA: "RECEP",
  EXTRAORDINARIA: "EXTRA", DESMOBILIZACAO: "DESMOB", INSTRUMENTISTA: "INSTRUM", PLANEJAMENTO: "PLANEJ", MOBILIZACAO: "MOB",
  ENCARREGADO: "ENCARREG", ENCARREGADA: "ENCARREG", CALDEIREIRO: "CALD", COORDENADOR: "COORD",
  CONSERVACAO: "CONSERV", GALVANIZADO: "GALV", GALVANIZADA: "GALV", MANUTENCAO: "MANUT", DISPOSICAO: "DISP",
  INSTALACAO: "INST", TRANSPORTE: "TRANSP", SUPERVISOR: "SUPERV", ENGENHEIRO: "ENG", ALMOXARIFE: "ALMOX",
  ASSISTENTE: "ASSIST", TUBULACAO: "TUBU", MOTORISTA: "MOTORI", SEGURANCA: "SEGUR", ALPINISTA: "ALPIN",
  ADICIONAL: "ADIC", VIGILANTE: "VIG", PERICULOSO: "PERIC", PERICULOSA: "PERIC", INSALUBRE: "INSAL",
  AUXILIAR: "AUX", AJUDANTE: "AJUD", MECANICO: "MEC", OPERADOR: "OP", OFICIAL: "OF", TECNICO: "TEC", TECNICA: "TEC",
  TRABALHO: "TRAB", RESIDUAL: "RESID", EVENTUAL: "EVENTU", LIMPEZA: "LIMP", MAQUINA: "MAQ", SERVICOS: "SERV",
  CONCRETO: "CONC", DIAMETRO: "DIAM", NOTURNO: "NOT", NOTURNA: "NOT", CAIXA: "CX"
};
const RESERVADAS = new Set([...Object.values(PALAVRAS), "ELET", "ELETR", "PERI", "MANU", "MANT", "PLAN", "CORD", "NORM",
  "DISPOS", "SERVIC", "CONCR", "INSALUB", "PERICUL", "EXTRAORD", "NOTUR", "NOTURN", "EVENTUA", "HNOT", "HINS", "HEXTRA"]);
const CURTAS = { COM: "C/", SEM: "S/", PARA: "P/" };
const MINIMAS = ["DE", "DA", "DO", "DAS", "DOS", "E", "EM", "NO", "NA"];
const VOGAL = /[AEIOU]/;
const CARGO = /^(ELETRICISTA|SOLDADOR|PEDREIRO|PINTOR|CARPINTEIRO|ENCANADOR|MONTADOR|MARTELETEIRO|MACARIQUEIRO|JARDINEIRO|TORNEIRO|ARMADOR|TELHADISTA|VIDRACEIRO|SERRALHEIRO|INSPETOR|SERVENTE|ANDAIME|ANDAIMISTA|RESGATISTA|ZELADOR|DEDETIZADOR|ANALISTA|PROJETISTA|LAMINADOR|CONTROLADOR|PORTEIRO|BOMBEIRO|MECANICA|ELETRICA|CIVIL)$/;

function encurtar(palavra) {
  const s = semAcento(palavra);
  if (s.length < 7 || !/^[A-Z]+$/.test(s)) return null;
  for (let n = 4; n <= 7 && n < s.length - 2; n++) {
    if (VOGAL.test(s[n]) && !VOGAL.test(s[n - 1]) && !RESERVADAS.has(s.slice(0, n))) return palavra.slice(0, n);
  }
  return null;
}

function caber40(texto) {
  let t = texto.toUpperCase().replace(/\s+/g, " ").trim();
  const original = t;
  const cabe = () => t.length <= 40;
  for (const [longo, curto] of FRASES) {
    if (cabe()) break;
    const m = semAcento(t).match(new RegExp("\\b" + longo.replace(/ /g, "\\s+") + "\\b"));
    if (m) t = t.slice(0, m.index) + curto + t.slice(m.index + m[0].length);
  }
  let p = t.split(" ");
  for (let i = p.length - 1; i > 0 && !cabe(); i--) {
    const w = semAcento(p[i]);
    if (CURTAS[w]) { p[i] = CURTAS[w]; t = p.join(" "); }
    else if (MINIMAS.includes(w) && i < p.length - 1) { p.splice(i, 1); t = p.join(" "); }
  }
  const trocar = troca => {
    const ordem = p.map((w, i) => [w, i]).sort((x, y) => y[0].length - x[0].length);
    for (const [w, i] of ordem) {
      if (cabe()) return;
      const novo = troca(w);
      if (novo && novo.length < w.length) { p[i] = novo; t = p.join(" "); }
    }
  };
  trocar(w => PALAVRAS[semAcento(w)]);
  trocar(w => !CARGO.test(semAcento(w)) && encurtar(w));
  trocar(encurtar);
  const cortou = !cabe();
  if (cortou) t = t.slice(0, 40).trim();
  return { texto: t, abreviou: t !== original && !cortou, cortou, original };
}

const COMANDOS = /^(buscar|busca|procurar|procura|consultar|consulta|pesquisar|pesquisa|criar|cria|cadastrar|novo c[oó]digo( de)?)\s+/;

function semAcento(t) {
  return t.normalize("NFD").replace(/\p{M}/gu, "");
}

function numerosPorExtenso(texto) {
  const palavras = texto.split(/\s+/);
  const saida = [];
  let i = 0;
  while (i < palavras.length) {
    const p = semAcento(palavras[i]);
    if (!(p in NUMEROS)) { saida.push(palavras[i]); i++; continue; }
    const grupo = [];
    let j = i;
    while (j < palavras.length) {
      const q = semAcento(palavras[j]);
      if (q in NUMEROS) { grupo.push(NUMEROS[q]); j++; continue; }
      if (q === "e" && j + 1 < palavras.length && semAcento(palavras[j + 1]) in NUMEROS && grupo.length) { j++; continue; }
      break;
    }
    const soUm = grupo.length === 1 && grupo[0] === 1;
    const depois = semAcento(palavras[j] || "");
    if (soUm && !/^(%|x|por|vezes|mm|cm|m|m2|m3|pol|hora|horas|h)$/.test(depois)) {
      saida.push(palavras[i]); i++; continue;
    }
    let total = 0, parcial = 0;
    for (const n of grupo) {
      if (n === 1000) { total += (parcial || 1) * 1000; parcial = 0; } else parcial += n;
    }
    saida.push(String(total + parcial));
    i = j;
  }
  return saida.join(" ");
}

function falaParaTexto(falado) {
  let t = " " + falado.toLowerCase().trim() + " ";
  t = t.replace(/[.,;!?]+(?=\s)/g, " ").replace(/\s+/g, " ").trim();
  let acao = null;
  const comando = t.match(COMANDOS);
  if (comando) {
    acao = /^(criar|cria|cadastrar|novo)/.test(comando[1]) ? "criar" : "buscar";
    t = t.slice(comando[0].length);
  }
  let unidade = null;
  const un = t.match(/\s(?:na unidade|unidade)\s+(.+)$/);
  if (un && un[1].split(" ").length <= 3) {
    unidade = un[1];
    t = t.slice(0, un.index);
  }
  t = numerosPorExtenso(t.replace(/\bpor ?cento\b/g, "%"));
  for (const [de, para] of TROCAS) t = t.replace(de, para);
  t = t.replace(/(\d+)\s+v[ií]rgula\s+(\d+)/g, "$1,$2").replace(/(\d+)\s*(?:x|por|vezes)\s*(\d+)/g, "$1X$2").replace(/(\d)\s+%/g, "$1%").replace(/\s+/g, " ").trim();
  const curto = caber40(t);
  return { texto: curto.texto, abreviou: curto.abreviou, cortou: curto.cortou, completo: t.toUpperCase(), acao, unidade };
}

if (typeof module !== "undefined") module.exports = { falaParaTexto, caber40 };
