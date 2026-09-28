# TV Atalhos (AmigoTv)

**Resolve o problema das boxes Android chinesas (X96Q, X96, MXQ, H96, T95, …) que não deixam adicionar certas apps ao ecrã inicial.**

## O problema

Instala uma app como a **amigo tv** (Vodafone), Disney+ ou outra app feita para Android TV. A app instala bem, mas:

- não aparece no ecrã inicial da box;
- no botão **"+"** do ecrã inicial ela não aparece para adicionar;
- para a abrir tem de ir à Play Store → *As minhas apps* → *Abrir*.

**Porquê?** Estas apps só têm ícone de *Android TV* (`LEANBACK_LAUNCHER`). O ecrã inicial de fábrica destas boxes (ex.: `com.droidlogic.xlauncher`) só mostra apps com ícone de telemóvel (`LAUNCHER`). A app está lá, o ecrã inicial é que não a vê.

## A solução

A **TV Atalhos** é uma app pequena (~30 KB), sem anúncios, sem internet e sem recolha de dados, que:

| Função | O que faz |
|---|---|
| **Lista todas as apps** | Mostra também as apps só de TV. Carregue **OK** para abrir. |
| **Atalho 1 / 2 / 3** | Cria um ícone *"Atalho N"* que **aparece no "+" do ecrã inicial de fábrica**. Adicione-o ao ecrã inicial e ele abre diretamente a app escolhida. |
| **Abrir ao ligar a box** | A app escolhida (ex.: amigo tv) abre sozinha uns segundos depois de ligar a box. |

## Instalação

1. Descarregue **`TvAtalhos.apk`** da página [Releases](../../releases) (ou da pasta do repositório).
2. Instale na box de uma destas formas:
   - por **pen USB**: copie o APK, abra com o *FileManager* da box e instale (aceite "fontes desconhecidas");
   - por **Downloader** / browser da box, com o link do APK;
   - por **ADB**: `adb connect IP_DA_BOX:5555` e depois `adb install TvAtalhos.apk`.
3. Se aparecer o aviso do *Play Protect*, escolha **"Instalar mesmo assim"**.
4. **Abra a TV Atalhos uma vez**, porque o Android não deixa uma app correr no arranque se nunca foi aberta.

## Como usar (com o comando)

1. Abra a **TV Atalhos** (aparece no "+" do ecrã inicial e na lista de apps).
2. Escolha a app (ex.: *amigo tv*) e **mantenha OK carregado** ou carregue no botão **MENU**.
3. Escolha:
   - **Abrir automaticamente ao ligar a box**; ou
   - **Pôr no Atalho 1** (ou 2, 3).
4. Para o atalho: vá ao ecrã inicial da box → **"+"** → marque **"Atalho 1"**. Esse ícone passa a abrir a app escolhida.

O botão **Ajuda** dentro da app explica o mesmo no ecrã da TV.

### Permissão para "Abrir ao ligar"

A partir do Android 10, só se pode abrir uma app no arranque com a permissão **"Sobrepor a outras apps"**. A TV Atalhos abre esse ecrã automaticamente. Se a sua box não tiver esse ecrã, dê a permissão por ADB:

```
adb shell appops set pt.tvatalhos SYSTEM_ALERT_WINDOW allow
```

## Problemas comuns

- **O "Atalho N" não aparece no "+"**: reinicie a box. Alguns ecrãs iniciais só atualizam a lista depois de reiniciar.
- **A app não abre sozinha ao ligar**: confirme a permissão acima e que abriu a TV Atalhos pelo menos uma vez. Algumas boxes têm um "gestor de arranque"/"limpeza de memória" que bloqueia apps. Nesse caso, ponha a TV Atalhos como permitida.
- **Os ícones dos atalhos dizem "Atalho 1/2/3" e não o nome da app**: é uma limitação do Android, porque o nome do ícone é fixo dentro do APK. A lista de cima na TV Atalhos mostra a que app corresponde cada atalho.

## Compilar (programadores)

Não precisa de Android Studio nem de Gradle. Em Debian/Ubuntu (incluindo Raspberry Pi):

```
sudo apt install aapt apksigner dalvik-exchange android-sdk-platform-23 openjdk-17-jdk-headless zipalign python3-pil
./build.sh
```

Gera `TvAtalhos.apk`. Na primeira vez cria a chave de assinatura `release.jks`. Guarde-a (não vai para o git), porque as atualizações têm de ser assinadas com a mesma chave.

Estrutura:

- `AndroidManifest.xml`: atividade principal, 3 *activity-alias* de atalho (desativados até serem usados) e o recetor de arranque.
- `src/pt/tvatalhos/`: `MainActivity` (lista e opções), `SlotActivity` (abre a app de um atalho), `BootReceiver` (abre a app no arranque), `Apps` (utilitários e configuração).
- `tools/icons.py`: gera os ícones.

## Licença

MIT
