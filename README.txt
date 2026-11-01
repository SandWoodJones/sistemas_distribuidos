===============================================================================
 Sistema Inteligente de Reserva de Salas  --  EP-1
 Sistemas Distribuidos  --  BCC / UTFPR-PG  --  Prof. Dr. Richard Ribeiro
 Aluno: Raul Rodrigues de Oliveira  --  RA 2422727
===============================================================================

1. O QUE ESTA NESTE PACOTE
---------------------------------------------------------------------------
    README.txt      este arquivo
    pom.xml         descritor de build (Maven), opcional
    libs/           TODAS as bibliotecas necessarias (ver secao 6)
    src/main/java   codigo fonte do cliente e do servidor
    src/main/resources/logback.xml   configuracao de log
    src/test/java   testes automatizados (JUnit 5, nao necessarios para rodar)

Nao ha banco de dados no pacote: o arquivo reservas.db e criado
automaticamente na primeira execucao do servidor (ver secao 5).


2. REQUISITOS
---------------------------------------------------------------------------
    Java 17 ou superior (JDK para compilar, JRE para apenas executar).
    Verifique com:   java -version

Nada mais e necessario. Nao e preciso Maven, nem internet, nem instalar
SQLite: o driver e as bibliotecas nativas ja estao em libs/.


3. COMPILAR (sem Maven - recomendado)
---------------------------------------------------------------------------
Abra um terminal na pasta onde este README esta e execute:

  Linux / macOS:

    mkdir -p classes
    javac -encoding UTF-8 -cp "libs/*" -d classes $(find src/main/java -name '*.java')
    cp src/main/resources/logback.xml classes/

  Windows (Prompt de Comando):

    mkdir classes
    dir /s /b src\main\java\*.java > fontes.txt
    javac -encoding UTF-8 -cp "libs\*" -d classes @fontes.txt
    copy src\main\resources\logback.xml classes\

A compilacao nao deve emitir nenhum erro.


4. EXECUTAR
---------------------------------------------------------------------------
Use DOIS terminais, um para o servidor e outro para o cliente, ambos
abertos na mesma pasta deste README.

  SERVIDOR  (a porta e o argumento da linha de comando; omitida, usa 5000)

    Linux / macOS:  java -cp "classes:libs/*" reservas.server.ServerApp 5000
    Windows:        java -cp "classes;libs\*" reservas.server.ServerApp 5000

    IMPORTANTE: execute o servidor SEMPRE a partir de um terminal, nunca
    por duplo clique. E no terminal que aparecem as linhas "recebido" e
    "enviado" de cada mensagem trocada.

  CLIENTE  (interface grafica Swing)

    Linux / macOS:  java -cp "classes:libs/*" reservas.client.ClientApp
    Windows:        java -cp "classes;libs\*" reservas.client.ClientApp

    Na janela do cliente ha campos para o IP e para a porta do servidor a
    ser contatado, e um painel que mostra todas as mensagens enviadas e
    recebidas. Para testar na mesma maquina, use IP 127.0.0.1.


5. BANCO DE DADOS E USUARIO ADMINISTRADOR
---------------------------------------------------------------------------
Na primeira execucao o servidor cria o arquivo reservas.db na pasta
corrente e cadastra um administrador inicial:

    usuario: admin      senha: admin      email: admin@admin.com

Isso aparece no terminal como:
    "administrador inicial criado: user admin, email admin@admin.com"

O protocolo nao possui operacao para criar o primeiro administrador (todo
register nasce com role "user"), por isso ele e semeado junto com o
esquema do banco. Apagar reservas.db zera o sistema e recria o admin na
execucao seguinte.


6. BIBLIOTECAS INCLUIDAS (pasta libs/)
---------------------------------------------------------------------------
    gson-2.11.0.jar                  serializacao/parse do JSON do protocolo
    sqlite-jdbc-3.47.1.0.jar         driver JDBC do SQLite, com as
                                     bibliotecas nativas embutidas
    slf4j-api-2.0.17.jar             fachada de log
    logback-classic-1.5.18.jar       implementacao de log
    logback-core-1.5.18.jar          idem
    error_prone_annotations-2.27.0.jar  dependencia transitiva do Gson

Os testes usam JUnit 5, que NAO esta em libs/ por nao ser necessario para
compilar nem executar o programa. Para roda-los e preciso Maven com
acesso a internet (secao 7).


7. COMPILAR E TESTAR COM MAVEN (opcional)
---------------------------------------------------------------------------
Com Maven instalado e acesso a internet (ele baixa os plugins de build,
que nao acompanham este pacote):

    mvn test          executa a suite de testes
    mvn package       gera target/servidor.jar e target/cliente.jar,
                      auto-contidos; rodam com
                          java -jar target/servidor.jar 5000
                          java -jar target/cliente.jar

Se nao houver internet, use o procedimento da secao 3, que nao depende
de Maven.


8. OPERACOES IMPLEMENTADAS NESTA ENTREGA (EP-1)
---------------------------------------------------------------------------
    register       cadastro de usuario comum          (C)
    read_user      leitura do proprio cadastro        (R)
    update_user    atualizacao do proprio cadastro    (U)
    delete_user    exclusao do proprio cadastro       (D)
    login          autenticacao, devolve token
    logout         encerra a sessao

As operacoes de salas, reservas e administracao (admin_*) pertencem a
entrega final e nao fazem parte da EP-1.


9. PROTOCOLO
---------------------------------------------------------------------------
Implementa o protocolo de troca de mensagens do grupo, versao 2.4:

    https://pedrosalvatori.github.io/protocolo-reserva-salas/

Resumo do enquadramento: uma mensagem e um objeto JSON em UMA linha,
terminada por \n, em UTF-8, com no maximo 8192 bytes incluindo o \n.
Todos os valores sao strings, as chaves sao em ingles e snake_case, e
campos desconhecidos sao ignorados pelo receptor -- o que permite a
interoperacao com os clientes e servidores dos outros grupos.
