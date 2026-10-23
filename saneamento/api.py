import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

from lote import MAXIMO, PlanilhaInvalida, exportar_planilha, ler_planilha
from similaridade import carregar_buscador

LIMITE = 10_000
LIMITE_LOTE = 20_000_000
XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"


class Handler(BaseHTTPRequestHandler):
    buscador = None

    def _responder(self, status, corpo):
        dados = json.dumps(corpo, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(dados)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(dados)

    def _arquivo(self, dados, tipo):
        self.send_response(200)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers()
        self.wfile.write(dados)

    def _corpo(self):
        limite = LIMITE_LOTE if self.path.startswith(("/lote", "/codigos/lote")) else LIMITE
        if "chunked" not in (self.headers.get("Transfer-Encoding") or "").lower():
            tamanho = int(self.headers.get("Content-Length") or 0)
            return self.rfile.read(tamanho) if 0 < tamanho <= limite else None
        partes, total = [], 0
        while True:
            tamanho = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
            if tamanho == 0:
                break
            total += tamanho
            if total > limite:
                return None
            partes.append(self.rfile.read(tamanho))
            self.rfile.readline()
        while self.rfile.readline() not in (b"\r\n", b"\n", b""):
            pass
        return b"".join(partes)

    def _novo(self, p):
        codigo, descricao, unidade, grupo = p.get("codigo"), p.get("descricao"), p.get("unidade"), p.get("grupo") or ""
        textos = (codigo, descricao, unidade, grupo)
        if not all(isinstance(v, str) for v in textos) or not all(v.strip() for v in textos[:3]):
            return self._responder(400, {"erro": "codigo, descricao e unidade são obrigatórios"})
        if len(codigo) > 10 or len(descricao) > 60 or len(unidade) > 5 or len(grupo) > 40:
            return self._responder(400, {"erro": "texto grande demais"})
        if codigo.strip() in self.buscador.oficial.index:
            return self._responder(409, {"erro": "esse código já existe"})
        novo = self.buscador.adicionar(codigo.strip(), descricao.strip().upper(), unidade.strip().upper(), grupo.strip())
        self._responder(201, novo)

    def _lote(self, p):
        itens = p.get("itens")
        if not isinstance(itens, list) or not 0 < len(itens) <= MAXIMO:
            return self._responder(400, {"erro": f"envie de 1 a {MAXIMO} itens"})
        pares = []
        for i in itens:
            d, u = (i.get("descricao"), i.get("unidade") or "") if isinstance(i, dict) else (None, None)
            if not isinstance(d, str) or not isinstance(u, str) or len(d) > 200 or len(u) > 5:
                return self._responder(400, {"erro": "cada item precisa de descricao (até 200) e unidade (até 5)"})
            pares.append((d, u.strip().upper()))
        self._responder(200, {"itens": self.buscador.buscar_lote(pares)})

    def _novos(self, p):
        itens = p.get("itens")
        if not isinstance(itens, list) or not 0 < len(itens) <= MAXIMO:
            return self._responder(400, {"erro": f"envie de 1 a {MAXIMO} códigos"})
        limpos, vistos = [], set()
        for i in itens:
            if not isinstance(i, dict) or not all(isinstance(i.get(k), str) and i.get(k).strip() for k in ("codigo", "descricao", "unidade")):
                return self._responder(400, {"erro": "cada código precisa de codigo, descricao e unidade"})
            cod = i["codigo"].strip()
            if cod in vistos or cod in self.buscador.oficial.index:
                continue
            vistos.add(cod)
            limpos.append({"codigo": cod, "descricao": i["descricao"].strip().upper()[:60], "unidade": i["unidade"].strip().upper()[:5],
                           "grupo": str(i.get("grupo") or "")[:40], "oficial": str(i.get("oficial") or "").strip() or None})
        self._responder(201, {"itens": self.buscador.adicionar_varios(limpos) if limpos else []})

    def do_OPTIONS(self):
        self._responder(204, {})

    def do_GET(self):
        if self.path == "/saude":
            return self._responder(200, {"status": "ok", "codigos": len(self.buscador.c)})
        self._responder(404, {"erro": "caminho não existe"})

    def do_POST(self):
        if self.path not in ("/similares", "/codigos", "/codigos/lote", "/lote", "/lote/ler", "/lote/exportar"):
            return self._responder(404, {"erro": "caminho não existe"})
        try:
            corpo = self._corpo()
        except ValueError:
            corpo = None
        if not corpo:
            return self._responder(400, {"erro": "corpo vazio ou grande demais (máximo 20 MB)"})
        if self.path == "/lote/ler":
            try:
                return self._responder(200, ler_planilha(corpo, unquote(self.headers.get("X-Arquivo") or "")))
            except PlanilhaInvalida as e:
                return self._responder(400, {"erro": str(e)})
        try:
            pedido = json.loads(corpo)
        except (ValueError, UnicodeDecodeError):
            return self._responder(400, {"erro": "JSON inválido"})
        if not isinstance(pedido, dict):
            return self._responder(400, {"erro": "JSON inválido"})
        if self.path == "/codigos":
            return self._novo(pedido)
        if self.path == "/codigos/lote":
            return self._novos(pedido)
        if self.path == "/lote":
            return self._lote(pedido)
        if self.path == "/lote/exportar":
            linhas = pedido.get("linhas")
            if not isinstance(linhas, list) or not all(isinstance(l, dict) for l in linhas):
                return self._responder(400, {"erro": "envie as linhas para exportar"})
            return self._arquivo(exportar_planilha(linhas), XLSX)
        descricao, unidade, limite = pedido.get("descricao"), pedido.get("unidade") or "", pedido.get("limite", 10)
        if not isinstance(descricao, str) or not descricao.strip() or len(descricao) > 200:
            return self._responder(400, {"erro": "descricao é obrigatória (até 200 caracteres)"})
        if not isinstance(unidade, str) or len(unidade) > 5:
            return self._responder(400, {"erro": "unidade inválida"})
        if not isinstance(limite, int) or not 1 <= limite <= 50:
            return self._responder(400, {"erro": "limite deve ser um número de 1 a 50"})
        self._responder(200, self.buscador.buscar(descricao, unidade.strip().upper(), limite))


def servir(caminho, host="127.0.0.1", porta=8000):
    Handler.buscador = carregar_buscador(caminho)
    servidor = ThreadingHTTPServer((host, porta), Handler)
    print(f"ok: {len(Handler.buscador.c)} códigos carregados; API em http://{host}:{porta}")
    return servidor


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("planilha", nargs="?", default="dados/base_codigos_servico.xlsx")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--porta", type=int, default=8000)
    a = ap.parse_args()
    servir(a.planilha, a.host, a.porta).serve_forever()
