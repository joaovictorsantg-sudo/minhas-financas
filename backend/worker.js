/**
 * Backend do Minhas Finanças — Cloudflare Worker.
 *
 * Existe por um motivo só: o CLIENT_SECRET da Pluggy não pode ficar numa
 * página pública. O app chama este worker, o worker fala com a Pluggy.
 *
 * Segredos (painel da Cloudflare → Settings → Variables → Encrypt):
 *   PLUGGY_CLIENT_ID      da aplicação criada no dashboard da Pluggy
 *   PLUGGY_CLIENT_SECRET  idem
 *   APP_TOKEN             senha longa que só você e o app conhecem
 *   ORIGEM                https://SEU-USUARIO.github.io
 */

const PLUGGY = 'https://api.pluggy.ai';

export default {
  async fetch(req, env, ctx) {
    const origem = env.ORIGEM || '*';
    const cors = {
      'Access-Control-Allow-Origin': origem,
      'Access-Control-Allow-Headers': 'content-type, x-app-token',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Max-Age': '86400',
    };
    if (req.method === 'OPTIONS') return new Response(null, { headers: cors });

    const json = (o, s = 200) =>
      new Response(JSON.stringify(o), {
        status: s,
        headers: { ...cors, 'content-type': 'application/json; charset=utf-8' },
      });

    // a URL do worker é pública: sem o token, ninguém usa sua cota nem seus dados
    const enviado = req.headers.get('x-app-token') || '';
    if (!env.APP_TOKEN || !iguais(enviado, env.APP_TOKEN)) return json({ erro: 'nao_autorizado' }, 401);

    const url = new URL(req.url);
    const rota = url.pathname.replace(/\/+$/, '') || '/';

    try {
      if (rota === '/connect-token') {
        // token curto e de escopo reduzido, para o widget abrir no navegador
        const itemId = url.searchParams.get('itemId') || undefined;
        const r = await pluggy(env, '/connect_token', {
          method: 'POST',
          body: JSON.stringify(itemId ? { itemId } : {}),
        });
        return json({ accessToken: r.accessToken });
      }

      if (rota === '/contas') {
        const itemId = url.searchParams.get('itemId');
        if (!itemId) return json({ erro: 'falta_itemId' }, 400);
        const r = await pluggy(env, `/accounts?itemId=${encodeURIComponent(itemId)}`);
        return json({ contas: (r.results || []).map(contaLimpa) });
      }

      if (rota === '/lancamentos') {
        const accountId = url.searchParams.get('accountId');
        if (!accountId) return json({ erro: 'falta_accountId' }, 400);
        const desde = url.searchParams.get('desde'); // AAAA-MM-DD
        const tudo = [];
        // v2 pagina por cursor. Nada de page nem pageSize aqui: qualquer um
        // deles devolve a chamada ao /transactions antigo, que responde 410.
        let q = '?accountId=' + encodeURIComponent(accountId) +
                (desde ? '&dateFrom=' + encodeURIComponent(desde) : '');
        for (let i = 0; i < 20; i++) {
          const r = await pluggy(env, `/v2/transactions${q}`);
          tudo.push(...(r.results || []).map(lancLimpo));
          if (!r.next) break;
          // next já vem com os filtros e o cursor; colar como veio.
          // O cursor é base64 e tem +, / e =: reencodar quebra a próxima página.
          q = String(r.next).startsWith('?') ? r.next : '?' + String(r.next).replace(/^&/, '');
        }
        return json({ lancamentos: tudo });
      }

      if (rota === '/investimentos') {
        const itemId = url.searchParams.get('itemId');
        if (!itemId) return json({ erro: 'falta_itemId' }, 400);
        const tudo = [];
        for (let pagina = 1; pagina <= 10; pagina++) {
          const q = new URLSearchParams({ itemId, pageSize: '500', page: String(pagina) });
          const r = await pluggy(env, `/investments?${q}`);
          tudo.push(...(r.results || []).map(invLimpo));
          if (!r.results?.length || pagina >= (r.totalPages ?? 1)) break;
        }
        return json({ investimentos: tudo });
      }

      // A fatura do cartão existe pronta na Pluggy: datas e total, sem precisar
      // deduzir ciclo a partir de transações soltas.
      if (rota === '/faturas') {
        const accountId = url.searchParams.get('accountId');
        if (!accountId) return json({ erro: 'falta_accountId' }, 400);
        const tudo = [];
        for (let pagina = 1; pagina <= 10; pagina++) {
          const q = new URLSearchParams({ accountId, pageSize: '100', page: String(pagina) });
          const r = await pluggy(env, `/bills?${q}`);
          tudo.push(...(r.results || []).map(faturaLimpa));
          if (!r.results?.length || pagina >= (r.totalPages ?? 1)) break;
        }
        return json({ faturas: tudo });
      }

      if (rota === '/item') {
        const itemId = url.searchParams.get('itemId');
        if (!itemId) return json({ erro: 'falta_itemId' }, 400);
        const r = await pluggy(env, `/items/${encodeURIComponent(itemId)}`);
        return json({ id: r.id, status: r.status, erro: r.error, banco: r.connector?.name, atualizadoEm: r.lastUpdatedAt });
      }

      if (rota === '/') return json({ ok: true, servico: 'minhas-financas' });
      return json({ erro: 'rota_desconhecida' }, 404);
    } catch (e) {
      return json({ erro: 'falha_pluggy', detalhe: String(e && e.message || e) }, 502);
    }
  },
};

/* a API key vale 2 horas; guardá-la evita bater no limite do /auth */
let chave = { valor: null, expiraEm: 0 };

async function apiKey(env) {
  if (chave.valor && Date.now() < chave.expiraEm) return chave.valor;
  const r = await fetch(`${PLUGGY}/auth`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ clientId: env.PLUGGY_CLIENT_ID, clientSecret: env.PLUGGY_CLIENT_SECRET }),
  });
  if (!r.ok) throw new Error(`auth ${r.status}: ${(await r.text()).slice(0, 200)}`);
  const d = await r.json();
  if (!d.apiKey) throw new Error('auth sem apiKey');
  chave = { valor: d.apiKey, expiraEm: Date.now() + 100 * 60 * 1000 }; // renova antes das 2h
  return chave.valor;
}

async function pluggy(env, caminho, opts = {}) {
  const chamar = async (k) =>
    fetch(`${PLUGGY}${caminho}`, {
      ...opts,
      headers: { 'content-type': 'application/json', 'X-API-KEY': k, ...(opts.headers || {}) },
    });
  let r = await chamar(await apiKey(env));
  if (r.status === 401 || r.status === 403) {
    chave = { valor: null, expiraEm: 0 }; // chave venceu antes do previsto
    r = await chamar(await apiKey(env));
  }
  if (!r.ok) throw new Error(`${caminho} ${r.status}: ${(await r.text()).slice(0, 200)}`);
  return r.json();
}

/* só o que o app usa sai daqui: nada de número de conta ou documento */
const contaLimpa = (c) => ({
  id: c.id,
  nome: c.name,
  tipo: c.type,                      // BANK | CREDIT
  subtipo: c.subtype,
  banco: c.marketingName || c.name,
  saldo: c.balance,
  limite: c.creditData?.creditLimit ?? null,
  fecha: c.creditData?.balanceCloseDate ?? null,
  vence: c.creditData?.balanceDueDate ?? null,
});

const invLimpo = (i) => ({
  id: i.id,
  nome: i.name,
  codigo: i.code || '',            // ticker nas ações, CNPJ nos fundos
  tipo: i.type,                    // EQUITY | ETF | FIXED_INCOME | MUTUAL_FUND | ...
  subtipo: i.subtype || '',        // STOCK | REAL_ESTATE_FUND | ...
  saldo: i.balance,                // valor líquido hoje
  aplicado: i.amount ?? null,      // bruto aplicado, com impostos
  cotas: i.quantity ?? null,
  unitario: i.value ?? null,
});

// a fatura vem pronta da Pluggy: datas e total, sem deduzir ciclo
const soDia = (d) => (typeof d === 'string' && d.length >= 10 ? d.slice(0, 10) : null);
const cent = (v) => (v == null || isNaN(+v) ? null : Math.round(+v * 100) / 100);
const faturaLimpa = (b) => {
  const pgs = Array.isArray(b.payments) ? b.payments : [];
  return {
    id: b.id,
    vence: soDia(b.dueDate),
    fecha: soDia(b.billClosingDate),
    total: cent(b.totalAmount),
    minimo: cent(b.minimumPaymentAmount),
    // o banco sabe o que já foi pago: sem isso toda fatura antiga volta em aberto
    pago: pgs.length ? cent(pgs.reduce((s, p) => s + Math.abs(+p.amount || 0), 0)) : null,
    pagoEm: pgs.map((p) => soDia(p.paymentDate)).filter(Boolean).sort().pop() || null,
  };
};

const lancLimpo = (t) => ({
  id: t.id,
  data: String(t.date || '').slice(0, 10),
  valor: t.amount,                   // negativo é saída
  descricao: t.description,
  categoria: t.category || '',
  tipo: t.type,                      // DEBIT | CREDIT
});

/* comparação em tempo constante, para o token não vazar por tempo de resposta */
function iguais(a, b) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}
