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

Faturas de cartão em **PDF** também são lidas, para os bancos que só
oferecem esse formato. O pdf.js vai junto no repositório, em `vendor/`,
carregado só quando um PDF é escolhido — nada de CDN, e funciona offline
depois da primeira vez.

Numa fatura em PDF todos os lançamentos vão para a fatura daquele
vencimento, inclusive parcelas compradas meses antes, que é como o
cartão de fato cobra. Fatura fechada costuma já estar paga: marque-a
como paga em *Cartões* depois de importar, senão ela prende limite à
toa.

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

## Sincronização bancária

Em **Ajustes → Sincronização**, informando o endereço do backend e o
token, o app conecta bancos pelo Open Finance e puxa os lançamentos sem
baixar extrato. O widget da Pluggy vai junto no repositório, em
`vendor/`, carregado só quando você conecta um banco.

Cada conta da Pluggy é casada automaticamente com uma conta ou cartão do
app pelo nome, e cada lançamento já chega com o destino certo — conta
vai para conta, cartão vai para a fatura. O que vem sincronizado passa
pela mesma tela de revisão do extrato, com a mesma detecção de
transferência e de repetidos, então sincronizar duas vezes não duplica
nada.

Investimentos também vêm pelo mesmo caminho: posição, quantidade de
cotas e valor aplicado saem da corretora, o que dispensa digitar
quantidade e torna a cotação manual desnecessária para o que estiver
sincronizado. Antes de confirmar, a tela mostra em quanto o patrimônio
investido vai ficar e avisa quais posições suas não casaram com nenhuma
do banco — é assim que se percebe que a mesma aplicação está prestes a
entrar duas vezes.

Na virada do ciclo uma fatura pode fechar e vencer em datas que não são
as de sempre, ou deixar de existir, quando o banco junta dois ciclos num
só. Ambos se ajustam por fatura, e o dia de fechamento escolhido decide
quais compras caem nela e quais passam para a seguinte.

No extrato de um cartão vêm, junto das compras, o pagamento da própria
fatura, amortizações e ajustes. Cada um deles carrega o valor da fatura
inteira, então entram como se fossem a maior compra do mês e dobram o
que já estava lá. São reconhecidos e chegam desmarcados; os que já
entraram antes disso aparecem na tela de cartões para apagar.

Corrigir à mão o limite ou as datas de um cartão faz a sincronização
parar de mexer neles. O banco demora a refletir uma mudança de
fechamento, e sem isso a correção era desfeita na sincronização seguinte.
Dá para devolver o controle ao banco na mesma tela.

A tela é uma coluna da altura da janela, com cabeçalho e barra de abas
presos nas pontas e só o miolo rolando. Instalado, o Android desenha o
app sob a barra de status e a de gestos, então a janela inclui o espaço
delas: o layout reserva esse espaço pelas medidas que o sistema informa,
com um mínimo próprio para quando ele informa zero.

Gasto no crédito lançado à mão na hora da compra é reconhecido quando o
banco enfim o entrega, dias depois e com outro nome: casam por cartão,
valor e proximidade de data. O lançamento continua sendo o seu, com a
sua descrição e categoria, e passa a carregar o identificador do banco
para não voltar como novidade.

O extrato filtra por três eixos ao mesmo tempo: o que (gasto, entrada,
aporte), onde (débito, crédito ou uma conta específica) e categoria. Os
filtros ativos ficam visíveis como etiquetas, cada uma com o seu próprio
desfazer, para que ninguém leia uma lista parcial achando que é tudo.

Regras de categoria do próprio usuário vêm antes das do app e ficam no
aparelho: um Pix para uma pessoa pode ser a mensalidade da faculdade, e
isso ninguém adivinha pela descrição. A regra casa por texto, ignorando
acento e caixa, e aceita um valor exato para separar o que vai sempre
para a mesma pessoa por motivos diferentes.

O orçamento pode ser calculado a partir da meta de investir. Cada
categoria com nome recebe o que de fato consome, medido desde o mês em
que apareceu e nunca abaixo do ritmo do mês corrente; "Outros" fica com o
resto, em vez de abocanhar o orçamento por ser o maior monte.

A tela de planejamento abre pelo que já aconteceu: quanto saiu no mês
contra a mediana dos meses fechados, o que subiu e o que caiu em cada
categoria, os maiores gastos e os avisos do que destoa. Teto por
categoria continua existindo, abaixo disso, para quem quiser um limite —
mas orçamento por envelope pressupõe despesa fixa, e nem todo mundo tem.

Passar do teto numa categoria não é furo enquanto outra deixou
sobra: o teto que importa é o do mês. A tela soma a sobra de todas e diz
se o excesso está coberto, quanto ainda resta livre, ou quanto falta
quando nem a sobra fecha a conta.

Aporte de dinheiro que o banco já conhece não mexe em saldo nem em
posição: a conta já foi descontada e o valor já está na posição que vem
da corretora. O registro serve para o mês saber quanto foi investido,
que é a única parte que não dá para deduzir do que o banco informa.

Orçamento não definido não é orçamento de zero. Sem ele a tela de
planejamento mostra o gasto do mês e convida a definir um, em vez de
acusar estouro contra uma meta que ninguém escreveu.

A fatura do cartão vem pronta do banco: data de fechamento, de
vencimento, total e os pagamentos feitos nela — quem sabe se foi paga é
quem recebeu. Ela manda sobre o ciclo deduzido, o que
resolve sozinho a virada do dia de fechamento e o mês em que o banco não
emitiu fatura nenhuma. O ajuste que completa a diferença entre o total
do banco e os lançamentos já conhecidos é refeito a cada sincronização,
nunca somado, e some conforme os lançamentos reais explicam o valor. Saldos e faturas são atualizados mesmo
quando não há lançamento novo: eles mudam sozinhos.

Repetido é reconhecido pelo id do banco e também pela data, valor e
descrição. Reconectar um banco gera ids novos para as mesmas transações,
e só pelo id o histórico inteiro voltaria como novidade. O que conta é quantas já existem,
não se alguma existe: duas passagens iguais no mesmo dia são duas
passagens, mesmo chegando em sincronizações diferentes.

Duas contas do banco não podem apontar para a mesma conta do app: o
acerto de saldo de uma sobrescreve o da outra, e quem aplicar por último
decide. Um mapeamento assim já gravado é desfeito na sincronização
seguinte.

Lançamento que aponta para uma conta apagada é sinalizado na tela
inicial, com a opção de mandar para outra conta ou apagar. Ele continuava
somando no mês e em nenhum saldo, o que faz o dinheiro em conta aparecer
como zero sem explicação.

Conta ou cartão que o banco traz pode ser criado ali mesmo, com nome,
limite e datas de fechamento vindos dele. Sem isso, quem ainda não tinha
cadastrado nada abria uma lista vazia e não tinha o que apontar.

Restaurar um backup preserva a conexão com o banco: ela é deste aparelho
e não do arquivo, e um backup gravado antes de conectar não deve desfazer
o que já está ligado.

Conta que o banco traz e o app ainda não conhece fica esperando um
destino, e só depois de apontado o extrato dela é buscado — desde o
começo, não a partir da data em que foi apontada. A marca de "já li até
aqui" é por conta, não por banco, senão apontar uma conta nova congelaria
o histórico dela no dia.

Renda fixa com o mesmo nome entra numa linha só: o Porquinho do Inter
abre um CDB por depósito, e sem juntar eles o app ganharia dezenas de
ativos de treze reais. Cotas aparecem apenas em ação, onde querem dizer
alguma coisa.

Quando um banco falha e outro funciona, a revisão diz qual falhou e por
quê. Sem isso a falha passava calada: bastava o banco devolver os
investimentos para a tela abrir como se tudo tivesse vindo, e o extrato
que faltava só aparecia como ausência.

Sincronizar lê a cópia que a Pluggy mantém do banco, refeita uma vez por
dia. Reler força essa releitura onde a conexão aceita; a do plano
gratuito não aceita por caminho nenhum, e nesse caso o app explica que
ela é feita em meu.pluggy.ai. O que o banco ainda não processou continua
sem aparecer de qualquer forma.

A sincronização acontece sozinha ao abrir o app, no máximo de quatro em
quatro horas, e nunca interrompe: se vier coisa nova, a tela inicial
avisa e você revisa quando quiser. Navegador não roda app em segundo
plano de forma confiável, então não existe sincronização de hora em hora
com o app fechado. Quem preferir pode marcar *Lançar sem revisar*, e aí
o que chega entra direto com a categoria adivinhada — transferência e
repetido continuam de fora.

O token fica no `localStorage`, fora do backup, para não viajar junto
quando você levar seus dados para outro aparelho.

Como publicar o backend está em [`backend/README.md`](backend/README.md).

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
