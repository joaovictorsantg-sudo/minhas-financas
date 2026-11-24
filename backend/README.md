# Backend

Um único Cloudflare Worker, de graça, que existe por um motivo: o
`CLIENT_SECRET` da Pluggy não pode ficar numa página pública. Qualquer
pessoa que abrisse o código veria, e com ele leria suas contas.

O app chama este worker, o worker fala com a Pluggy.

## O que cada rota faz

| Rota | Serve para |
|---|---|
| `POST /connect-token` | Devolve um token curto, de escopo reduzido, para o widget da Pluggy abrir no navegador |
| `GET /contas?itemId=` | Contas e cartões daquela conexão, com saldo e limite |
| `GET /lancamentos?accountId=&desde=` | Lançamentos da conta, paginados até o fim |
| `GET /item?itemId=` | Como está a conexão e quando foi atualizada |

A API Key da Pluggy vale duas horas e fica só na memória do worker,
renovada sozinha. Nenhuma rota devolve número de conta ou documento.

## Publicar

Dá para fazer inteiro pelo navegador, sem instalar nada.

1. Crie uma aplicação no **dashboard da Pluggy** e anote o `CLIENT_ID` e
   o `CLIENT_SECRET`.
2. Em **dash.cloudflare.com** → *Workers & Pages* → *Create* →
   *Start from Hello World* → *Deploy*.
3. Abra o worker → *Edit code*, apague o exemplo, cole o conteúdo de
   `worker.js` e *Deploy*.
4. No worker → *Settings* → *Variables and Secrets*, adicione as quatro,
   as três primeiras como **Secret**:

   | Nome | Valor |
   |---|---|
   | `PLUGGY_CLIENT_ID` | do dashboard da Pluggy |
   | `PLUGGY_CLIENT_SECRET` | do dashboard da Pluggy |
   | `APP_TOKEN` | uma senha longa e aleatória, que você também coloca no app |
   | `ORIGEM` | `https://SEU-USUARIO.github.io` |

5. Copie a URL do worker, algo como
   `https://minhas-financas.SEU-NOME.workers.dev`, e informe no app em
   *Ajustes → Sincronização*.

## Por que o APP_TOKEN

A URL do worker é pública. Sem uma senha, qualquer um que a descobrisse
gastaria sua cota da Pluggy e leria seus dados. Todo pedido precisa do
cabeçalho `x-app-token`, conferido em tempo constante. O `ORIGEM`
restringe quem pode chamar pelo navegador, mas não substitui o token:
cabeçalho `Origin` se forja fora do navegador.

## Testar sem o app

```sh
curl -H "x-app-token: SEU_TOKEN" https://SEU-WORKER.workers.dev/
# {"ok":true,"servico":"minhas-financas"}
```

Sem o token a resposta é `401 nao_autorizado`, que é o esperado.
