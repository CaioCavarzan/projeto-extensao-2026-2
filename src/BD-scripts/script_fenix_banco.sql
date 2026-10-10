-- Criar e usar banco 

create database banco_fenix
go

use banco_fenix
go

-- 1. Tabelas independentes (sem chaves estrangeiras)

create table Enderecos 
(
    codigo     int identity    not null,
    uf         char   (2)      not null,
    cidade     varchar(60)     not null,
    cep        char   (8)      not null,
    logradouro varchar(100)    not null,
    nr_imovel  varchar(10)             ,

    -- Restricoes --
    constraint pk_codigo primary key (codigo)
)
go

sp_help Enderecos
go

create table FontesAquisicao 
(
    codigo	  int identity     not null,
    descricao varchar     (50)         , -- Precisão e escala aplicam-se só a numéricos (ex: DECIMAL, INT). Em texto/data ficam vazios por definição do tipo, sem relação com NOT NULL.
    
    -- Restricoes --
    constraint pk_fontes_aquisicao primary key (codigo)
)
go

sp_help FontesAquisicao
go

create table StatusItens
(
    codigo int identity     not null,
    status varchar     (25) not null,

	-- Restricoes -- 
	constraint pk_status_itens      primary key   (codigo),
    constraint uq_status_itens_nome unique        (status) -- Não pode haver duplicidade de status da peça
)
go

sp_help StatusItens
go

create table Cores 
(
    codigo int identity not null,
    nome   varchar(50)  not null,

    -- Restricoes --
    constraint pk_cores      primary key (codigo),
    constraint uq_cores_nome unique      (nome)
)
go

sp_help Cores
go

create table StatusOs 
(
    codigo int identity not null,
    status varchar(50)  not null,

    -- Restricoes --
    constraint pk_statusos primary key (codigo)
)
go

sp_help StatusOs
go

 create table RotasProcessos 
(
    codigo int identity not null,
    nome   varchar(25)  not null,

    -- Restricoes --
    constraint pk_rotasprocessos primary key (codigo)
)
go

sp_help RotasProcessos
go

-- 2. Entidades base

create table Pessoas 
(
    codigo           int identity  not null,
    nome             varchar(200)          ,
    email            varchar(150)          ,
    telefone         varchar(20)           ,  
    endereco_codigo  int           not null,
   
    -- Restricoes --
    constraint pk_pessoas           primary key (codigo)                                        ,
    constraint fk_pessoas_enderecos foreign key (endereco_codigo) references Enderecos (codigo)
)
go

sp_help Pessoas
go

-- 3. Herancas

create table Clientes 
(
    pessoa_codigo int not null, -- FK de Pessoas           
    fonte_codigo  int not null, -- FK de FontesAquisicao
    observacao    varchar(300),
    prioridade    int         ,

    -- Restricoes --
    constraint pk_clientes                    primary key (pessoa_codigo)                                    ,
    constraint fk_clientes_pessoas            foreign key (pessoa_codigo) references Pessoas        (codigo) ,
    constraint fk_clientes_fontes_aquisicao   foreign key (fonte_codigo)  references FontesAquisicao(codigo)
)
go

sp_help Clientes
go

create table PessoasFisicas
(
    cliente_codigo int         not null, -- FK de Cliente
    cpf            char   (11) not null, -- Exatamente apenas os números sem máscara no banco
    nome_social    varchar(200)        ,

    -- Restricoes --
    constraint pk_pessoas_fisicas                primary key (cliente_codigo)                                    ,
    constraint uq_pessoas_fisicas_cpf            unique      (cpf)                                               , -- Não poder haver duplicidade de cpf
    constraint fk_pessoas_fisicas_clientes       foreign key (cliente_codigo) references Clientes(pessoa_codigo)
)
go

sp_help PessoasFisicas
go

create table PessoasJuridicas
(
    cliente_codigo int          not null, -- FK de Cliente
    cnpj           char   (14)  not null, -- Tem exatamente 14 numeros
    nome_fantasia  varchar(100)         ,
    ie             varchar(14)          , -- Inscrição Estadual

    -- Restricoes --
    constraint pk_pessoas_juridicas          primary key (cliente_codigo)                                    ,
    constraint uq_pessoas_juridicas_cnpj     unique      (cnpj)                                              , -- Não pode haver duplicidade de cnpj
    constraint fk_pessoas_juridicas_clientes foreign key (cliente_codigo) references Clientes(pessoa_codigo)
)
go

sp_help PessoasJuridicas

create table Funcionarios 
(
    pessoa_codigo int               not null, -- FK de Pessoas
    cpf           char(11)          not null, -- Apenas números
    cargo         varchar(50)       not null,
    salario       decimal(18,2)     not null,
    data_admissao datetime          not null,

    -- Restricoes --
    constraint pk_funcionarios         primary key (pessoa_codigo)                            ,
    constraint uq_funcionarios_cpf     unique      (cpf)                                      ,
    constraint fk_funcionarios_pessoas foreign key (pessoa_codigo) references Pessoas (codigo),
    constraint ck_funcionarios_salario check       (salario >= 0)
)
go

sp_help Funcionarios
go

create table Usuarios 
(  
    funcionario_codigo int          not null          , -- FK de Funcionarios
    is_admin           bit          not null default 0,
    login              varchar(150) not null          , 
    senha_hash         varchar(100) not null          ,
    ativo              bit          not null default 1,

    -- Restricoes --
    constraint pk_usuarios              primary key (funcionario_codigo)                                         ,
    constraint uq_usuarios_login        unique      (login)                                                      ,
    constraint fk_usuarios_funcionarios foreign key (funcionario_codigo) references Funcionarios (pessoa_codigo)
)
go

sp_help Usuarios
go

create table Fornecedores 
(
    pessoa_codigo   int         not null, -- FK de Pessoas
    cnpj            char   (14) not null,
    ativo           bit         not null, -- 1 para Ativo, 0 para Inativo
    prazo_pagamento varchar(50) not null,

    -- Restricoes --
    constraint pk_fornecedores         primary key (pessoa_codigo)                            ,
    constraint uq_fornecedores_cnpj    unique      (cnpj)                                     , -- Não pode haver duplicidade de cnpj
    constraint fk_fornecedores_pessoas foreign key (pessoa_codigo) references Pessoas(codigo)
)
go

sp_help Fornecedores

-- 4. Operacao (estoque, os, itens e cronograma)

create table Tintas 
(
    codigo            int identity   not null          ,
    fornecedor_codigo int            not null          , -- FK de Fornecedores
    cor_codigo        int            not null          , -- FK de Cores
    descricao         varchar(100)   not null          ,
    tipo              varchar(50)                      ,
    acabamento        varchar(50)                      ,
    ref_fornecedor    varchar(100)                     ,
    estoque_atual     decimal(10, 2) not null default 0,
    valor_unit        decimal(18, 2) not null          ,

    -- Restricoes --
    constraint pk_tintas              primary key (codigo)                                                    ,
    constraint fk_tintas_fornecedores foreign key (fornecedor_codigo) references Fornecedores (pessoa_codigo) ,
    constraint fk_tintas_cores        foreign key (cor_codigo)        references Cores (codigo)               ,
    constraint ck_tintas_valor        check       (valor_unit >= 0)                                           ,
    constraint ck_tintas_estoque      check       (estoque_atual >= 0)
)
go

sp_help Tintas
go

create table OrdensDeServico 
(   
    nr_os          int identity   not null,
    cliente_codigo int            not null, -- FK de Clientes(pessoa_codigo)
    usuario_codigo int            not null, -- FK de Usuarios(funcionario_codigo)
    data_entrada   datetime       not null, 
    valor_os       decimal(18, 2)         ,
    prioridade     int                    ,

    -- Restricoes --
    constraint pk_ordens_de_servico         primary key (nr_os)                                                  ,
    constraint fk_ordens_de_servico_cliente foreign key (cliente_codigo) references Clientes (pessoa_codigo)     ,
    constraint fk_ordens_de_servico_usuario foreign key (usuario_codigo) references Usuarios (funcionario_codigo),
    constraint ck_ordens_de_servico_valor   check       (valor_os >= 0)
)
go

sp_help OrdensDeServico
go

create table Itens 
(
    codigo           int identity   not null,
    os_codigo        int            not null, -- FK de OrdensDeServico(nr_os)
    tinta_codigo     int                    , -- FK de Tintas(codigo)
    cor_codigo       int                    , -- FK de Cores(codigo)
    descricao        varchar(100)   not null,
    qtd_total        decimal(10, 2) not null,
    unidade          varchar(10)    not null, -- Ex: KG, M, etc
    data_entrada     datetime       not null,
    prazo_entrega    date                   , 
    valor_total_item decimal(18, 2) not null,
    data_finalizacao datetime               , 
    nr_ose           varchar(50)            , 
    score_prioridade decimal(10, 2)         , 

    -- Restricoes --
    constraint pk_itens           primary key (codigo)                                                       ,
    constraint fk_itens_os        foreign key (os_codigo)    references OrdensDeServico (nr_os)              ,
    constraint fk_itens_tintas    foreign key (tinta_codigo) references Tintas (codigo)                      ,
    constraint fk_itens_cores     foreign key (cor_codigo)   references Cores (codigo)                       ,
    constraint ck_itens_qtd       check       (qtd_total > 0)                                                ,
    constraint ck_itens_valor     check       (valor_total_item >= 0)                                        ,
    constraint ck_itens_score     check       (score_prioridade is null or score_prioridade >= 0)            ,
    constraint ck_itens_datas_fim check       (data_finalizacao is null or data_finalizacao >= data_entrada)
)
go

sp_help Itens
go

create table Fotos 
(
    codigo      int identity   not null,
    item_codigo int            not null, -- FK de Itens(codigo)
    foto        varchar(max)   not null,

    -- Restricoes --
    constraint pk_fotos       primary key (codigo)                                ,
    constraint fk_fotos_itens foreign key (item_codigo) references Itens (codigo)
)
go

sp_help Fotos
go

create table Cronogramas
(
    codigo         int identity not null,
    usuario_codigo int          not null, -- FK de Usuarios(funcionario_codigo)
    data_planejada date         not null,
    observacao     varchar(255)         ,

    -- Restricoes --
    constraint pk_cronogramas          primary key  (codigo)                                                 ,
    constraint fk_cronogramas_usuarios foreign key (usuario_codigo) references Usuarios (funcionario_codigo)
)
go

sp_help Cronogramas
go

create table ItensRotasProcessos 
(
    rota_processo_codigo int         not null, -- FK de RotasProcessos(codigo)
    item_codigo          int         not null, -- FK de Itens(codigo)
    ordem                int         not null,
    data_inicio          datetime            , 
    data_conclusao       datetime            , 
    funcionario_codigo   int                 , 

    -- Restricoes --
    constraint pk_itens_rotas_processos              primary key (rota_processo_codigo, item_codigo)                           ,
    constraint fk_itens_rotas_processos_rotas        foreign key (rota_processo_codigo) references RotasProcessos (codigo)     ,
    constraint fk_itens_rotas_processos_itens        foreign key (item_codigo)          references Itens (codigo)              ,
    constraint fk_itens_rotas_processos_funcionarios foreign key (funcionario_codigo)   references Funcionarios (pessoa_codigo),
    constraint ck_itens_rotas_processos_ordem        check       (ordem > 0)                                                   ,
    constraint ck_itens_rotas_processos_datas        check       (data_conclusao >= data_inicio)
)
go

sp_help ItensRotasProcessos
go

-- 5. Associações (N:N) restantes

create table CronogramasItens 
(
    cronograma_codigo          int            not null, 
    rota_processo_codigo       int            not null,
    item_codigo                int            not null,
    qtd_planejado              decimal(10, 2) not null,
    posicao                    int            not null,
    pontuacao                  decimal(10, 2)         ,

    -- Restricoes --
    constraint pk_cronogramas_itens                   primary key (cronograma_codigo, rota_processo_codigo, item_codigo)                                               ,
    constraint fk_cronogramas_itens_cronograma        foreign key (cronograma_codigo)                 references Cronogramas (codigo)                                  ,
    constraint fk_cronogramas_itens_itens_rotas       foreign key (rota_processo_codigo, item_codigo) references ItensRotasProcessos(rota_processo_codigo, item_codigo),
    constraint ck_cronogramas_itens_qtd               check       (qtd_planejado > 0)                                                                                  ,
    constraint ck_cronogramas_itens_posicao           check       (posicao >= 0)                                                                                       ,
    constraint ck_cronogramas_itens_pontuacao         check       (pontuacao >= 0)  
)
go

sp_help CronogramasItens
go

create table HistoricosStatusOs 
(
    os_codigo     int      not null                  , -- FK de OrdensDeServico(nr_os)
    status_codigo int      not null                  , -- FK de StatusOs(codigo)
    data_hora     datetime not null default getdate(),

    -- Restricoes --
    constraint pk_historicos_status_os       primary key (os_codigo, status_codigo)                        ,
    constraint fk_historicos_statusos_os     foreign key (os_codigo)     references OrdensDeServico (nr_os),
    constraint fk_historicos_statusos_status foreign key (status_codigo) references StatusOs (codigo)
)
go

sp_help HistoricosStatusOs
go

create table HistoricosStatusItens 
(
    item_codigo        int      not null                  , -- FK de Itens(codigo)
    status_item_codigo int      not null                  , -- FK de StatusItens(codigo)
    data_hora          datetime not null default getdate(),

    -- Restricoes --
    constraint pk_historicos_status_itens        primary key (item_codigo, status_item_codigo)                    ,
    constraint fk_historicos_status_itens_item   foreign key (item_codigo)        references Itens (codigo)       ,
    constraint fk_historicos_status_itens_status foreign key (status_item_codigo) references StatusItens (codigo)
)
go

sp_help HistoricosStatusItens
go

