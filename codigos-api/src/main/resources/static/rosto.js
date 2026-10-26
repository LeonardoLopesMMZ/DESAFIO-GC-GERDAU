const MODELOS = "rosto/modelos";
let reconhecimento = null;
let repetirRosto = null;

function carregarReconhecimento() {
  if (reconhecimento) return reconhecimento;
  reconhecimento = new Promise((ok, erro) => {
    const s = document.createElement("script");
    s.src = "rosto/face-api.js";
    s.onload = ok;
    s.onerror = () => erro(falha("Não consegui carregar o reconhecimento de rosto."));
    document.head.appendChild(s);
  }).then(async () => {
    for (const b of ["webgl", "cpu"]) {
      try { if (await faceapi.tf.setBackend(b)) break; } catch (e) {}
    }
    await faceapi.tf.ready();
  }).then(() => Promise.all([
    faceapi.nets.tinyFaceDetector.loadFromUri(MODELOS),
    faceapi.nets.faceLandmark68TinyNet.loadFromUri(MODELOS),
    faceapi.nets.faceRecognitionNet.loadFromUri(MODELOS)
  ]));
  reconhecimento.catch(() => { reconhecimento = null; });
  return reconhecimento;
}

const cameraPossivel = () => window.isSecureContext && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
const esperar = ms => new Promise(r => setTimeout(r, ms));
const falha = texto => Object.assign(new Error(texto), { nosso: true });

function erroCamera(e) {
  const n = e && e.name;
  if (n === "NotAllowedError" || n === "SecurityError") return "A câmera foi bloqueada. Clique no cadeado da barra de endereço e permita a câmera.";
  if (n === "NotFoundError" || n === "OverconstrainedError") return "Nenhuma câmera encontrada neste computador.";
  if (n === "NotReadableError") return "A câmera está sendo usada por outro programa (Teams?). Feche e tente de novo.";
  if (e && e.nosso) return e.message;
  return "O reconhecimento falhou neste navegador. Use o Edge ou o Chrome atualizados.";
}

function recortar(video, caixa) {
  const lado = Math.min(Math.max(caixa.width, caixa.height) * 1.7, video.videoWidth, video.videoHeight);
  const x = Math.max(0, Math.min(video.videoWidth - lado, caixa.x + caixa.width / 2 - lado / 2));
  const y = Math.max(0, Math.min(video.videoHeight - lado, caixa.y + caixa.height / 2 - lado / 2));
  const c = document.createElement("canvas");
  c.width = c.height = 128;
  c.getContext("2d").drawImage(video, x, y, lado, lado, 0, 0, 128, 128);
  let q = 0.75, url = c.toDataURL("image/jpeg", q);
  while (url.length > 24000 && q > 0.3) { q -= 0.15; url = c.toDataURL("image/jpeg", q); }
  return url.slice(url.indexOf(",") + 1);
}

async function lerRosto(titulo) {
  const j = $("janela-rosto"), video = $("rosto-video"), info = $("rosto-info"), camera = $("rosto-camera");
  $("rosto-titulo").textContent = titulo;
  $("rosto-de-novo").classList.add("oculto");
  camera.className = "camera";
  camera.style.setProperty("--p", 0);
  info.classList.remove("erro-texto");
  info.textContent = "Ligando a câmera…";
  if (!j.open) j.showModal();
  let fluxo = null, cancelado = false;
  const fechou = () => { cancelado = true; };
  j.addEventListener("close", fechou, { once: true });
  try {
    if (!cameraPossivel()) throw falha("A câmera só funciona abrindo por http://localhost:8080 ou https.");
    fluxo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } }, audio: false });
    if (cancelado) throw falha("");
    video.srcObject = fluxo;
    await video.play();
    camera.classList.add("ligada");
    info.textContent = "Carregando o reconhecimento (só demora na primeira vez)…";
    await carregarReconhecimento();
    const opcoes = new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.5 });
    const leituras = [];
    let foto = null, melhor = 0;
    const limite = Date.now() + 25000;
    info.textContent = "Olhe para a câmera.";
    while (!cancelado && Date.now() < limite && leituras.length < 3) {
      const r = await faceapi.detectSingleFace(video, opcoes).withFaceLandmarks(true).withFaceDescriptor();
      if (cancelado) break;
      if (!r) { camera.classList.remove("achou"); info.textContent = "Não estou vendo um rosto. Olhe para a câmera."; await esperar(120); continue; }
      const caixa = r.detection.box;
      if (caixa.width < video.videoWidth * 0.2) { info.textContent = "Chegue um pouco mais perto."; await esperar(120); continue; }
      camera.classList.add("achou");
      if (r.detection.score > melhor) { melhor = r.detection.score; foto = recortar(video, caixa); }
      leituras.push(r.descriptor);
      camera.style.setProperty("--p", leituras.length / 3);
      info.textContent = "Fique parado…";
      await esperar(200);
    }
    if (cancelado) throw falha("");
    if (leituras.length < 3) throw falha("Não consegui ver o rosto direito. Melhore a luz e tente de novo.");
    const media = Array.from({ length: 128 }, (_, i) => leituras.reduce((s, l) => s + l[i], 0) / leituras.length);
    const variou = Math.max(...leituras.map(l => Math.hypot(...l.map((x, i) => x - media[i]))));
    if (variou > 0.35) throw falha("A leitura variou muito. Fique parado e tente de novo.");
    camera.classList.add("ok");
    info.textContent = "Conferindo…";
    return { descritor: media.map(x => Math.round(x * 1e5) / 1e5), foto };
  } finally {
    j.removeEventListener("close", fechou);
    if (fluxo) fluxo.getTracks().forEach(t => t.stop());
    video.srcObject = null;
    camera.classList.remove("ligada");
  }
}

function falhaRosto(e) {
  const j = $("janela-rosto");
  if (!j.open) return;
  const msg = erroCamera(e);
  if (!msg) { j.close(); return; }
  if (!(e && (e.nosso || e.name))) console.error(e);
  $("rosto-camera").className = "camera erro";
  $("rosto-info").textContent = msg;
  $("rosto-info").classList.add("erro-texto");
  $("rosto-de-novo").classList.remove("oculto");
}

async function entrarComRosto() {
  repetirRosto = entrarComRosto;
  try {
    const r = await lerRosto("Entrar com o rosto");
    const s = await chamar("/api/acesso/rosto/entrar", r, { semToken: true }).catch(e => { throw falha(e.message); });
    s.foto_local = "data:image/jpeg;base64," + r.foto;
    $("janela-rosto").close();
    entrou(s);
  } catch (e) { falhaRosto(e); }
}

async function cadastrarRosto() {
  repetirRosto = cadastrarRosto;
  try {
    const r = await lerRosto("Cadastrar meu rosto");
    const s = await chamar("/api/acesso/rosto/cadastrar", r).catch(e => { throw falha(e.message); });
    definirSessao(Object.assign({}, sessao, s, sessao.metodo === "ROSTO" ? {} : { foto_local: "data:image/jpeg;base64," + r.foto }));
    $("janela-rosto").close();
    $("janela-conta").close();
    aviso("Rosto cadastrado! Da próxima vez, entre com o rosto. Pelo nome, ninguém mais entra como você.", "LIBERAR");
  } catch (e) { falhaRosto(e); }
}

$("entrar-rosto").addEventListener("click", entrarComRosto);
$("cadastrar-rosto").addEventListener("click", cadastrarRosto);
$("rosto-de-novo").addEventListener("click", () => repetirRosto && repetirRosto());
