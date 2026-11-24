# Minhas Finanças

App de controle financeiro pessoal: gastos por categoria, cartões com fatura e
parcelas, investimentos com cotação automática da B3, e metas.

É um PWA: um arquivo HTML, sem build, sem servidor. Hospede em qualquer lugar
que sirva arquivos estáticos por HTTPS e ele instala na tela inicial do celular.

## Publicar no GitHub Pages

1. Crie um repositório novo e envie estes arquivos para a branch `main`.
2. No repositório: **Settings → Pages**.
3. Em *Source*, escolha **Deploy from a branch**; branch `main`, pasta `/ (root)`.
4. Salve. Em um ou dois minutos o site fica no ar em
   `https://SEU-USUARIO.github.io/NOME-DO-REPO/`.

## Instalar no celular

Abra o endereço no Chrome do Android, toque nos três pontinhos e escolha
**Instalar app** (ou *Adicionar à tela inicial*). O ícone aparece junto dos
outros apps e abre em tela cheia, sem barra de navegador.

No iPhone é pelo Safari: botão de compartilhar → *Adicionar à Tela de Início*.

## Cotações

As cotações vêm da [brapi](https://brapi.dev).

`ITUB4`, `PETR4`, `VALE3` e `MGLU3` funcionam sem token. Para qualquer outra
ação, crie um token gratuito em brapi.dev e cole em **Ajustes → Cotações**. O
token fica no `localStorage` do aparelho e nunca entra no código nem no
repositório.

O app busca os preços sozinho ao abrir, se a última atualização tiver mais de
15 minutos. Em *Investir → atualizar agora* dá para forçar.

## Primeiro acesso

O app começa vazio, sem dados de ninguém. Preencha em **Ajustes** o salário, os
saldos das contas e o orçamento por categoria, e cadastre os cartões na aba
*Cartões*. Se você já tem um backup, é mais rápido restaurar por
**Ajustes → Backup → Restaurar backup**.

## Importar extrato do banco

Em *Extrato* há o botão **Importar extrato do banco**. Ele lê arquivos
**OFX** e **CSV** — formatos que Inter, Bradesco, PicPay e a maioria dos
bancos oferecem na tela de extrato.

O app adivinha a categoria pela descrição, marca o que já foi lançado
antes para não duplicar, e deixa você revisar linha a linha antes de
confirmar. Escolha no topo se aquele extrato é de uma conta ou de um
cartão; em cartão, os pagamentos de fatura são ignorados, já que não são
gastos novos.

### O que o app faz sozinho ao importar

- Descobre se o arquivo é de conta ou de cartão, e de qual banco, pelo
  próprio conteúdo.
- Marca o meio de pagamento de cada linha: débito, crédito, Pix, boleto.
- Reconhece transferência entre contas suas — aplicação e resgate de
  investimento, crédito liberado, pagamento de fatura — e deixa
  desmarcada, porque não é gasto nem receita.
- Quando o extrato da conta traz o pagamento de uma fatura que existe no
  app, quita essa fatura e devolve o limite, em vez de lançar um gasto
  solto. O casamento é por valor; não havendo valor igual, pela data de
  vencimento mais próxima.
- Em OFX com saldo de fechamento, acerta o saldo da conta para bater com
  o extrato.

## Recomeçar

**Ajustes → Recomeçar** tem três opções, da mais branda à mais radical:
apagar só os lançamentos, zerar o saldo anterior dos cartões, ou limpar
tudo. Cada uma mostra antes o que some e o que fica, e nenhuma dá para
desfazer — baixe um backup se tiver dúvida.

Zerar o saldo anterior é o passo necessário antes de importar o extrato
de um cartão que já tinha fatura lançada à mão: sem isso, o mesmo gasto
entra duas vezes.

## Onde ficam os dados

Tudo no `localStorage` do aparelho. Nada sai dali: não há servidor, conta nem
telemetria, e nenhum dado financeiro fica no código ou no repositório — por isso
o repositório pode ser público sem expor nada seu.

Como consequência, os dados não passam de um aparelho para outro sozinhos. Em
**Ajustes → Backup** dá para baixar um `.json` com tudo e restaurar em outro
celular ou depois de limpar o navegador. Vale baixar um backup de vez em quando.

## Arquivos

| Arquivo | Para que serve |
|---|---|
| `index.html` | O app inteiro: markup, estilo e lógica |
| `manifest.webmanifest` | Nome, cores e ícones da instalação |
| `sw.js` | Service worker, faz funcionar offline |
| `icons/` | Ícones do app |

## Mexer no código

Abra `index.html` e edite. Os valores iniciais (salário, contas, cartões,
categorias, ativos e metas) ficam no objeto `DEF`, logo no começo do script —
mas só valem no primeiro acesso, antes de existir algo salvo no aparelho.

Depois de publicar uma versão nova, o service worker atualiza o app na próxima
vez que ele for aberto.
