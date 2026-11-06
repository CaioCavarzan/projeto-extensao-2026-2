-- =============================================================================================
-- Carga de dados COMPLETA do banco_fenix (versão 2) - gerada em 22/10/2026
-- Substitui o PROVISORIO-FENIX_INSERTS_BANCO_SQL.sql: agora as 22 tabelas têm dados.
--
-- Origem dos dados:
--   Planilha Controle de Serviço Interno (abas OS e Itens e Peças) ... 12 OSs e os 27 itens delas (numeradas de 1 a 12 no banco; o número da planilha fica no comentário)
--   Planilha Controle de Clientes (aba Identificação) ................ 26 clientes (nome, contato, telefone, CNPJ/IE quando existe)
--   Planilha Controle de Estoque de Tinta (abas Cadastros e Cores) ... 55 cores e 58 tintas
--   Planilha FENIX_FINANCEIRO_OFICIAL (aba Credores) ................. fornecedores e cargos da equipe
--   Integrantes do grupo (tabela enviada em 22/10) ................... 4 funcionários 'Admin / Sistema', com acesso de administrador
--   INVENTADO (dados fictícios, só para teste): CPF e CNPJ (com dígitos verificadores válidos), e-mails (@example.com),
--   telefones, endereços de São José do Rio Preto (ruas reais, números e CEPs sorteados), salários, datas de admissão,
--   pergunta e resposta de segurança, prazo de pagamento dos fornecedores (30, 60 ou 90 dias), preço de 20 tintas que
--   estavam sem preço, valor de 8 itens que estavam sem valor, datas das etapas de produção, cronogramas, histórico de
--   status e fotos (imagens ilustrativas, ver abaixo).
--
-- Rodar uma vez, logo depois do script_banco.sql, com o banco vazio. Tudo roda numa transação só:
-- se um insert falhar, nada fica gravado.
--
-- FOTOS: a coluna Fotos.foto guarda o NOME DO ARQUIVO (UUID.jpg) dentro da pasta configurada em
-- fenix.fotos.diretorio (application.properties). Copie os arquivos da pasta 'fotos' (que acompanha este script)
-- para essa pasta; sem os arquivos, a tela mostra o item sem imagem.
--
-- ACESSO: login = e-mail da pessoa; senha padrão Fenix@2026 (gravada só como hash BCrypt). A pergunta e a resposta de
-- segurança de cada usuário estão na planilha Excel que acompanha este script (aba Acessos de teste).
-- OSs: nr_os numerado de 1 a 12 (IDENTITY_INSERT, para não depender do contador); a próxima OS nova recebe 13.
-- =============================================================================================

use banco_fenix
go

set nocount on;
set xact_abort on;

begin try
begin transaction;

declare @endereco int, @pessoa int, @item int, @etapa int, @cronograma int;
declare @etapas table (chave varchar(20) primary key, codigo int not null);  -- código de cada etapa, para montar os cronogramas


-- ---------------------------------------------------------------------------------------------
-- 1. Domínios
-- ---------------------------------------------------------------------------------------------
insert into FontesAquisicao (descricao) values
    ('Indicação'), ('Google'), ('Instagram'), ('Facebook'), ('Anúncio Pago'), ('Site'), ('Não informada');
insert into StatusItens (status) values
    ('Pendente'), ('Aguardando execução'), ('Pausada'), ('Executada'), ('Cancelada');
insert into StatusOs (status) values
    ('Criada'), ('Em produção'), ('Pausada'), ('Concluída'), ('Cancelada');
insert into RotasProcessos (nome) values
    ('Jateamento'), ('Banho'), ('Pintura');
-- Cores: as da aba Cores da planilha de estoque, menos 'Jato' (é processo) e 'A Colocar' (cor ainda não definida).
insert into Cores (nome) values
    ('Aço Corten'),
    ('Alumínio'),
    ('Amarelo Brinquedo'),
    ('Amarelo Lipa'),
    ('Amarelo Montanha Russa'),
    ('Azul Brinquedo'),
    ('Azul Calcinha Lipa'),
    ('Azul Charles'),
    ('Azul Ladeia'),
    ('Azul Micro Texturizado'),
    ('Azul Painel'),
    ('Azul Placa'),
    ('Bege'),
    ('Branco Azulado Contaminado'),
    ('Branco Azulado Lipa'),
    ('Branco Fosco'),
    ('Branco Texturizado'),
    ('Branco Total'),
    ('Ceramica Texturizado'),
    ('Cinza 7024'),
    ('Cinza Cristal'),
    ('Cinza Cx D''Água'),
    ('Cinza Fosco'),
    ('Cinza Mesclado'),
    ('Cinza N6,5'),
    ('Cinza N6,5 Texturizado'),
    ('Cinza Prateado cx35'),
    ('Cinza Sub Gray'),
    ('Cinza Texturizado Escuro'),
    ('Colorido'),
    ('Dourado'),
    ('Laranja Brinquedo'),
    ('Marrom Bronze'),
    ('Marrom Fendi Champanhe'),
    ('Marrom Fino Rustico'),
    ('Marrom Ladeia'),
    ('Marrom Mesclado'),
    ('Marrom São Paulo'),
    ('Prata'),
    ('Prata Fosco'),
    ('Prata Metal 5'),
    ('Preto Fosco'),
    ('Preto Microtextura'),
    ('Preto Semi Brilho'),
    ('Preto Texturizado'),
    ('Preto Texturizado Contaminado'),
    ('Preto Texturizado Furado'),
    ('Rosa Brinquedo'),
    ('Verde Brinquedo'),
    ('Verde Lipa'),
    ('Verde Mato'),
    ('Verde Ral 6005'),
    ('Vermelho Brinquedo'),
    ('Verniz'),
    ('Vinho');

-- ---------------------------------------------------------------------------------------------
-- 2. Funcionários e usuários
--    Os 4 integrantes do grupo entram como funcionários 'Admin / Sistema' e usuários ADMINISTRADORES.
--    A equipe da Fênix (nomes e e-mails da carga anterior) entra como usuário comum. CPF, salário, data de admissão,
--    telefone, endereço e pergunta/resposta de segurança são INVENTADOS.
-- ---------------------------------------------------------------------------------------------
-- Pedro Henrique Cardoso Dos Santos Marques - Admin / Sistema
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025702', 'Rua Paraíba', '886');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Pedro Henrique Cardoso Dos Santos Marques', 'cardosopedrohenrique2006@gmail.com', '17991947714', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '01514830191', 'Admin / Sistema', 4500.00, '2026-02-02');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 1, 'cardosopedrohenrique2006@gmail.com', '$2a$10$ElnaDfkOPxzr2DeROg8ZRe346yR8iqIV4H8Ifq7vf6G8CDXElW.ae', 'Qual o nome do seu primeiro animal de estimação?', '$2a$10$d60OtVr8GTH7F7nLVhXGOOq.GyFWzoxAiNDYohiHDizXv6.hEuzy.', 1);
-- Caio Henrique Cavarzan - Admin / Sistema
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015163', 'Rua Antônio de Godoy', '2992');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Caio Henrique Cavarzan', 'caiocavarzan@gmail.com', '17996281162', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '18050960790', 'Admin / Sistema', 4200.00, '2026-02-02');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 1, 'caiocavarzan@gmail.com', '$2a$10$Cg9nJasPXCPPD9VbVC/zPOfm7g1FrNaoJKf8/DRs78a.mK7tMpodq', 'Qual o nome da cidade onde você nasceu?', '$2a$10$tWRs7PqCBVltcU6/5tXwyui3kGVesTUnI/Y39qqU5uBGpT9EoOYwq', 1);
-- Gustavo Henrique Vitor - Admin / Sistema
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025030', 'Rua Amazonas', '784');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Gustavo Henrique Vitor', 'pexepi123@gmail.com', '17991086484', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '48885047092', 'Admin / Sistema', 4200.00, '2026-02-02');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 1, 'pexepi123@gmail.com', '$2a$10$W7sZXGStSFXf6R.2o2UajuEfw5g72IXiMhUozG1PNr.h9tQuWqmyq', 'Qual seu time de futebol?', '$2a$10$KtKiy1cQ3bvOw9cIoEe2k.4O3OYTzHACUVKr97KmzvAF7gZEauxn.', 1);
-- Marcio Mateus Gonçalves Filho - Admin / Sistema
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025104', 'Rua Prudente de Moraes', '607');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Marcio Mateus Gonçalves Filho', 'goncalvesmarcio06@outlook.com', '17992699772', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '08068460705', 'Admin / Sistema', 4350.00, '2026-02-02');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 1, 'goncalvesmarcio06@outlook.com', '$2a$10$.wbx.ewP4VDsesuxx6hIPuvC0vF/eTiYRjaq8Curg2.8yNGCnfAMK', 'Qual foi sua primeira escola?', '$2a$10$0aS7.bnzqPTDEIpbkev.ie16mASDe/BaVsVQLr7cHue24slQtmv7i', 1);
-- Alessandro Ribeiro Marques - Sócio - Administrativo
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15093445', 'Avenida Anísio Haddad', '4882');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Alessandro Ribeiro Marques', 'fenix.afinanceiro@gmail.com', '17953598887', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '89552282799', 'Sócio - Administrativo', 6800.00, '2018-03-05');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 0, 'fenix.afinanceiro@gmail.com', '$2a$10$PBreE0lpHSZUCRdMc9.PcudFG8h0bgCIYoEjFdXUi4RqM7aRWGd2q', 'Qual o nome da sua mãe?', '$2a$10$qphHhN2cKK2KxdTSnR.KgeYuYjnPTirJKv5zmn/2yeC..mT8PSpe2', 1);
-- Vanessa Fabiana dos Santos Marques - Sócia - CEO
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015488', 'Rua Bernardino de Campos', '247');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Vanessa Fabiana dos Santos Marques', 'vanessafabianacardoso@gmail.com', '17984720079', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '76822739092', 'Sócia - CEO', 7200.00, '2018-03-05');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 0, 'vanessafabianacardoso@gmail.com', '$2a$10$TaVH8oU.QE4hBSi217h4i.w3glc5sMIBUyNT0W4IEQPk.CvkQ5Ftm', 'Qual o nome da sua mãe?', '$2a$10$EPM08XIFUb3xdWYfgrEt2uLJp2gsyoS1Bv1.EXpXJ3/Znqrv072T.', 1);
-- Richard - Pintor
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15045505', 'Avenida Murchid Homsi', '898');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Richard', 'pintor01.fenix@gmail.com', '17996895126', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '93045698778', 'Pintor', 2850.00, '2023-05-02');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 0, 'pintor01.fenix@gmail.com', '$2a$10$7wGVQr5gAnVtpbdnr9M.8.1EZUdRBccKeIWHo83Kt.x2lRg3Cze8O', 'Qual o nome do seu primeiro animal de estimação?', '$2a$10$mDumUmSdPrmvEhUB1AF0rOMBYdet0mMLkNZrZfOQzpwUYtIekjJLu', 1);
-- André - Jatista
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015611', 'Rua Marechal Deodoro', '1835');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('André', 'jatista01.fenix@gmail.com', '17919902373', @endereco);
set @pessoa = scope_identity();
insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao) values (@pessoa, '32665265974', 'Jatista', 2650.00, '2024-08-05');
insert into Usuarios (funcionario_codigo, is_admin, login, senha_hash, pergunta_seguranca, resposta_hash, ativo) values (@pessoa, 0, 'jatista01.fenix@gmail.com', '$2a$10$UiT2XqBA8/61pkSdfTIao.cJe72p2Bm6AO6U8jkcbuezEm55crdSG', 'Qual seu time de futebol?', '$2a$10$jznq/obzR74MG5Yqg5pKH.jnavoAEjlvi5W29bzClq6eAR582FNga', 1);

-- ---------------------------------------------------------------------------------------------
-- 3. Fornecedores (aba Credores + os dois que só aparecem nas tintas).
--    prazo_pagamento = quantos dias a Fênix combinou com o fornecedor para pagar as requisições: 30, 60 ou 90 dias
--    (valores combinados INVENTADOS: nenhuma planilha tem o prazo). CNPJ, e-mail, telefone e endereço INVENTADOS.
-- ---------------------------------------------------------------------------------------------
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15060204', 'Rua Vitorino de Lemos', '1731');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Epristintas', 'comercial.epristintas@example.com', '1730017837', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '76565093000163', 1, '30 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015689', 'Avenida Alberto Andaló', '540');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Inove / Realsi', 'comercial.inove.realsi@example.com', '1738368871', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '21197038000176', 1, '60 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15020849', 'Rua Campos Sales', '991');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Novar', 'comercial.novar@example.com', '1738429542', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '08843424000120', 1, '30 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15085756', 'Avenida José Munia', '217');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('WEG Tintas', 'comercial.weg.tintas@example.com', '17912926924', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '03195657000140', 1, '90 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015951', 'Avenida Coronel Spínola de Castro', '3542');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Concigas', 'comercial.concigas@example.com', '17925934719', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '70441584000116', 1, '30 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15085922', 'Avenida Francisco Chagas de Oliveira', '108');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Espafer - Granalha', 'comercial.espafer.granalha@example.com', '17964041288', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '54144989000136', 1, '60 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025742', 'Rua Goiás', '2450');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Calmon - Granalha', 'comercial.calmon.granalha@example.com', '17936051508', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '80557015000194', 1, '60 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015943', 'Avenida Duque de Caxias', '4192');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('FlexTintas', 'comercial.flextintas@example.com', '17945535103', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '80361052000122', 1, '30 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15020005', 'Rua XV de Novembro', '2365');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('5 S Tintas', 'comercial.5.s.tintas@example.com', '1731037608', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '52493479000149', 1, '90 dias');
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15090470', 'Avenida Lauro Pereira', '896');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Space Colors', 'comercial.space.colors@example.com', '1739868426', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '02153100000183', 1, '60 dias');

-- ---------------------------------------------------------------------------------------------
-- 4. Clientes (aba Identificação): 26 clientes, os das 12 OSs mais outros da planilha.
--    Telefone de contato e nome do contato vêm da planilha quando existem. CNPJ real só nos 3 que a planilha tem;
--    todos os outros CPF/CNPJ, e-mails e endereços são INVENTADOS. Fonte de aquisição: a da planilha quando existe.
--    Telefones gravados só com dígitos (o sistema grava assim e a tela aplica a máscara).
-- ---------------------------------------------------------------------------------------------
-- Vai e Vem (ID 1 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15062610', 'Rua Flavio Bianchini', '8445');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('VAI-VEM INDUSTRIA E COMERCIO DE BRINQUEDOS LTDA - EPP', 'compras.vai.e.vem@example.com', '17997232028', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Vai e Vem. Contato: Beto. Referência: Brinquedos Coloridos. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '66014614000130', 'Play Rio', '647090093110');
-- Silcar (ID 93 na planilha) - PJ; fonte da planilha
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15061460', 'Avenida Tarraf', '2570/2580');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Silcar Pneus LTDA F07', 'compras.silcar@example.com', '17991664633', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Silcar. Contato: Reinaldo. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '54376462000722', 'Silcar', '904491833183');
-- BSC Metal (ID 124 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025409', 'Avenida Juscelino Kubitschek de Oliveira', '1815');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('BSCMETAL PEÇAS PARA MOTOS LTDA', 'administrativo@bscmetal.com.br', '11913766124', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Instagram'), 'Nome usual: BSC Metal. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '43878052000178', 'BSC Metal', '473030561116');
-- Daniel Vitorelle (ID 90 na planilha) - PF; fonte da planilha
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15040412', 'Avenida João Batista Vetorasso', '4193');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Daniel Vitorelle', 'compras.daniel.vitorelle@example.com', '17991689020', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Daniel Vitorelle. Referência: Pçs Moto. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '27265212908', null);
-- Flávio Barros (ID 99 na planilha) - PF; fonte da planilha
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025123', 'Rua João Alves', '1238');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Flávio Barros', 'compras.flavio.barros@example.com', '17981280400', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Flávio Barros. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '04222013595', null);
-- Marlon (ID 92 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15046500', 'Avenida Teotônio Vilela', '893');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Marlon Reis', 'compras.marlon@example.com', '17996520342', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Facebook'), 'Nome usual: Marlon. Referência: Pçs Moto de Trilha Vermelha. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '02364996376', null);
-- Brazil Metais (ID 6 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15055383', 'Avenida Pedro Maria Gonçalves', '588');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Brazil Metais', 'compras.brazil.metais@example.com', '17991790498', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Não informada'), 'Nome usual: Brazil Metais. Contato: Moisés. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '24006427000165', 'Brazil Metais', null);
-- Ladeia (ID 2 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15050354', 'Avenida Bernardino Lopes', '996');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Ladeia', 'compras.ladeia@example.com', '17981995180', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Ladeia. Contato: Wilson. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '80818643000186', 'Ladeia', '648216005818');
-- Serralheria Primavera (ID 12 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025727', 'Avenida Rio Branco', '152');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Serralheria Primavera', 'compras.serralheria.primavera@example.com', '17996282417', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Serralheria Primavera. Contato: Pedro. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '71575655000136', 'Serralheria Primavera', null);
-- Expotec (ID 191 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15055366', 'Avenida Mirassol', '12');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Expotec', 'compras.expotec@example.com', '17935139839', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Expotec. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '16589834000114', 'Expotec', '364536894548');
-- Fernando Alvez (ID 179 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15047124', 'Avenida Nelson Cardoso Gonçalves', '1159');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Fernando Alvez', 'compras.fernando.alvez@example.com', '17981004664', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Fernando Alvez. Contato: Fernando. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '42364687276', null);
-- Celso Bike (ID 27 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15010956', 'Rua Eduardo Fonseca', '265');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Celso Bike', 'compras.celso.bike@example.com', '17909224834', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Instagram'), 'Nome usual: Celso Bike. Contato: Celso. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '67302899452', null);
-- SiloKing (ID 14 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015687', 'Rua Voluntários de São Paulo', '447');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('SiloKing', 'compras.siloking@example.com', '17966916386', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: SiloKing. Contato: Gustavo. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '17031517000140', 'SiloKing', '383028542290');
-- Cesar Belarmino (ID 3 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015708', 'Rua Pedro Amaral', '2439');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Cesar Belarmino', 'compras.cesar.belarmino@example.com', '17997736491', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Não informada'), 'Nome usual: Cesar Belarmino. Contato: Cesar Belarmino. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '95883479000168', 'Cesar Belarmino', null);
-- World Games (ID 78 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015929', 'Avenida Bady Bassitt Norte', '331');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('World Games', 'compras.world.games@example.com', '17972262432', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Facebook'), 'Nome usual: World Games. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '14975119000194', 'World Games', null);
-- Savio (ID 17 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15020326', 'Rua Castro Alves', '1394');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Savio', 'compras.savio@example.com', '17992323302', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Anúncio Pago'), 'Nome usual: Savio. Contato: Savio. Referência: Savio Vilela. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '92848109149', null);
-- Fabrício (ID 39 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015539', 'Rua General Glicério', '879');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Fabrício', 'compras.fabricio@example.com', '17991911620', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Fabrício. Contato: Fabrício. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '30325319189', null);
-- Sinual (ID 5 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15060144', 'Avenida Romeu Strazzi', '889');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Sinual', 'compras.sinual@example.com', '17991420500', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Sinual. Contato: Emília. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '19161414000120', 'Sinual', null);
-- Juliano (ID 8 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15060030', 'Avenida Cenobelino de Barros Serra', '4001');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Juliano', 'compras.juliano@example.com', '17991695149', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Juliano. Contato: Juliano. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '70072449000140', 'Juliano', '349392719970');
-- Lipa (ID 9 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015077', 'Avenida Bady Bassitt', '2813');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Lipa', 'compras.lipa@example.com', '17991294541', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Lipa. Contato: Lipa. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '14460234000126', 'Lipa', null);
-- Luiz Toten (ID 10 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15030741', 'Avenida Philadelpho Manoel Gouveia Neto', '1792');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Luiz Toten', 'compras.luiz.toten@example.com', '17988042464', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Não informada'), 'Nome usual: Luiz Toten. Contato: Luiz. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '32190546000149', 'Luiz Toten', null);
-- Serralheria Xavier (ID 13 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15010338', 'Rua Jorge Tibiriçá', '749');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Serralheria Xavier', 'compras.serralheria.xavier@example.com', '17991489398', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Serralheria Xavier. Contato: Breno. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '77339493000113', 'Serralheria Xavier', null);
-- Fino Rústico (ID 15 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15010233', 'Rua Cândido Rodrigues', '651');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Fino Rústico', 'compras.fino.rustico@example.com', '17991522780', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Site'), 'Nome usual: Fino Rústico. Contato: Zé. Referência: Zé. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '27720962000171', 'Fino Rústico', '533767340428');
-- UP Brasil (ID 16 na planilha) - PJ; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15060053', 'Avenida Feliciano Sales Cunha', '3024');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('UP Brasil', 'compras.up.brasil@example.com', '17997187067', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Facebook'), 'Nome usual: UP Brasil. Contato: Charles. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '08720187000100', 'UP Brasil', null);
-- Amarildo (ID 19 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025040', 'Avenida Presidente Castelo Branco', '100');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Amarildo', 'compras.amarildo@example.com', '17991120076', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Anúncio Pago'), 'Nome usual: Amarildo. Contato: Amarildo. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '94269099909', null);
-- Arnaldo (ID 20 na planilha) - PF; fonte inventada
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15012115', 'Avenida Carlos Gomes', '984');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Arnaldo', 'compras.arnaldo@example.com', '17981193790', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Arnaldo. Contato: Arnaldo. Referência: Trilhos Montanha Russa. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '07176274037', null);

-- ---------------------------------------------------------------------------------------------
-- 5. Tintas (aba Cadastros): as 58 de fornecedor identificado, de 101 cadastradas (as outras 43 não têm fornecedor).
--    Tipo e acabamento com a grafia da lista do sistema (Poliéster, Híbrido, Atlântico).
--    Preço: o da planilha; onde estava vazio (marcado 'preço inventado') foi sorteado entre 20 e 56 por kg.
-- ---------------------------------------------------------------------------------------------
insert into Tintas (fornecedor_codigo, cor_codigo, descricao, tipo, acabamento, ref_fornecedor, estoque_atual, valor_unit) values
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Branco Azulado Lipa'), 'Branco Azulado Brilhante Hibrido', null, null, 'B-1157', 13.50, 28.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto Semi Brilho Poliester', 'Poliéster', 'Brilhante', 'P-3304', 0.00, 35.20),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Azul Micro Texturizado'), 'Azul Microtextura Fosco Poliester', 'Poliéster', 'Fosco', 'Z-3901', 5.50, 52.62),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Rosa Brinquedo'), 'Lilás Liso Brilhante', 'Liso', 'Brilhante', 'PBY025', 1.20, 54.39),
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Azul Charles'), 'Azul Ral 5010', 'Poliéster', 'Texturizado', 'Ral 5010', 15.00, 30.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Amarelo Lipa'), 'Amarelo Ral 1003 Brilhante Hibrido', 'Híbrido', 'Brilhante', 'A-1001', 0.00, 36.71),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Vermelho Brinquedo'), 'Vermelho Coca Cola', 'Poliéster TGIC/BPA FREE', null, 'BT972309736', 33.00, 32.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Amarelo Brinquedo'), 'Amarelo PYB8', 'Poliéster', 'Liso', 'PYB8', 0.50, 30.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Laranja Brinquedo'), 'Laranja 2004', 'Poliéster', 'Liso', '2004', 1.00, 30.00),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Verde Ral 6005'), 'Verde Liso Brilhante Poliester', 'Poliéster', 'Brilhante', 'PBK004', 0.00, 30.98),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Amarelo Montanha Russa'), 'Amarelo Liso Brilhante', 'Poliéster', 'Brilhante', 'PBE003', 10.50, 33.93),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Azul Brinquedo'), 'Azul Liso Brilhante Ral 5010', 'Poliéster', 'Brilhante', 'PBJ053', 26.00, 27.68),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Verde Brinquedo'), 'Verde Liso Brilhante', 'Poliéster', 'Brilhante', 'PBK112', 2.20, 30.80),
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Cinza Sub Gray'), 'Grafite Sub Gray', 'Poliéster', 'Brilhante', 'Grafite Sub Gray', 15.00, 19.20),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Prata Metal 5'), 'Prata Ral 9007 Poliester Brilhante', 'Poliéster', 'Brilhante', 'W-2001', 62.00, 32.84),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Space Colors'), (select codigo from Cores where nome = 'Cinza 7024'), 'Cinza 7024', 'Poliéster', 'Liso', 'LE-C2034', 11.50, 27.09),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Cinza N6,5'), 'Cinza P Ral 7004 Liso Brilhante Hibrido', 'Híbrido', 'Liso Brilhante', 'C-1001', 0.00, 26.05),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Branco Total'), 'Branco Hibrido Brilhante Econ', 'Híbrido', 'Brilhante', 'B-1017e', 0.00, 26.52),
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Branco Total'), 'Branco Ral 9003', 'Poliéster', 'Liso Brilhante', 'Ral 9003', 0.00, 24.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Branco Fosco'), 'Branco R 9003 Poliester Fosco', 'Poliéster', 'Fosco', 'B-3607', 3.30, 39.60),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Branco Texturizado'), 'Branco Text Linea - Hibrido', 'Híbrido', 'Text Linea', 'B-1902', 0.00, 43.77),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Microtextura'), 'Preto Rio Preto Microtextura', 'Rio Preto', 'Microtextura', 'P-3915', 3.50, 42.89),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Fosco'), 'Preto Poliester Fosco', 'Poliéster', 'Fosco', 'P-3603', 2.00, 38.89),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Texturizado Furado'), 'Preto 9005 PPTX', 'Poliéster', 'Texturizado', 'PPTX', 3.80, 22.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Azul Placa'), 'Azul Poliester AZ-05', 'Poliéster', null, 'Z-3047', 8.50, 42.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Azul Painel'), 'Azul Hibrido Atlantico', 'Híbrido', 'Atlântico', 'Z-1007', 5.50, 62.50),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Verniz'), 'Verniz Cobre Poliester', 'Poliéster', null, 'PSW046', 19.00, 44.09),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Dourado'), 'Dourado Liso Perolizado', null, 'Liso Perolizado', 'W-1088', 15.00, 48.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Ceramica Texturizado'), 'Ceramica Poliester Microtextura', 'Poliéster', 'Microtextura', 'M-3930', 8.50, 31.01),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Marrom Bronze'), 'Bronze 1004 Anodic Acetinado', 'Anodic', 'Acetinado', 'M-3611', 0.00, 49.04),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto PPSB Ral 9005', 'Poliéster', 'Semi Brilho', 'PPSB', 0.00, 20.00),
    ((select codigo from Pessoas where nome = 'WEG Tintas'), (select codigo from Cores where nome = 'Cinza Fosco'), 'Politherm 16 R Li Grafite Ral 7024 Fo', 'Poliéster', 'Liso', '13648916', 2.00, 29.41),  -- preço inventado
    ((select codigo from Pessoas where nome = 'WEG Tintas'), (select codigo from Cores where nome = 'Cinza Mesclado'), 'Politherm 20 Li Cinza Mesclado 16380 Br', 'Poliéster', 'Liso', '10004935', 9.60, 38.55),  -- preço inventado
    ((select codigo from Pessoas where nome = '5 S Tintas'), (select codigo from Cores where nome = 'Marrom São Paulo'), 'Dourado', 'Poliéster', 'Liso Semi Fosco', 'PF182S', 16.00, 31.44),  -- preço inventado
    ((select codigo from Pessoas where nome = '5 S Tintas'), (select codigo from Cores where nome = 'Marrom Fino Rustico'), 'Marrom Ral 8002', 'Híbrido', 'Craqueado C/ Cobre Fosco', 'HM114C', 6.50, 26.91),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Verde Lipa'), 'Verde Liso Brilhante EP', 'Híbrido', 'Liso Brilhante', 'HGL-052', 0.00, 30.02),  -- preço inventado
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Marrom Fendi Champanhe'), 'Marrom Fendi Champanhe', null, null, null, 11.00, 32.68),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Azul Calcinha Lipa'), 'Azul Calcinha Lipa', null, null, null, 0.00, 39.55),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Vermelho Brinquedo'), 'Vermelho Pant. 187C/Ral 3002 Pol. Liso Brilh', 'Poliéster', 'Liso Brilhante', 'H-3001', 0.00, 25.85),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto Ral 9005', 'Poliéster', 'Liso Semi Brilho', 'Ral 9005', 0.00, 18.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Marrom Ladeia'), 'Marrom Ral 8014', 'Poliéster', 'Fosco', 'M-3600', 8.60, 44.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto Econômico Híbrido', 'Híbrido', 'Econômico', 'P-1304r', 25.00, 44.51),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Verde Ral 6005'), 'Verde Poliester', 'Poliéster', null, 'V-3001', 2.00, 48.20),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Texturizado'), 'Preto N1 Texturizado', 'Poliéster', 'Texturizado', 'TX971500136', 20.50, 22.80),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Cinza Cristal'), 'Cinza Cristal 1956 Tx', null, 'Texturizado', '1956', 3.00, 40.00),
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Cinza N6,5'), 'Cinza N.6,5', 'Poliéster', 'Liso BR', 'Cinza N.,5', 9.00, 19.20),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto N1 Semi Brilho', 'Poliéster', 'Semi Brilho', 'SB971500136', 24.50, 22.80),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Texturizado Contaminado'), 'Preto N1 Texturizado - TX971500136-Contaminado', 'Contaminado', 'Texturizado', 'TX971500136-Contaminado', 2.20, 22.80),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Branco Azulado Contaminado'), 'Branco Azulado Brilhante Hibrido - Contaminado', 'Contaminado', 'Azulado', 'B-1157 Contaminado', 0.00, 28.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Laranja Brinquedo'), 'Laranja Jacto L-3000', 'Poliéster', null, 'BT970936436', 20.00, 30.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Azul Ladeia'), 'Azul Bebê', 'Poliéster', 'Brilhante', 'Z-3119', 3.90, 48.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Branco Total'), 'Branco Brilhante', 'Poliéster', 'Brilhante', 'BT970500736', 102.00, 28.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Aço Corten'), 'Marrom Aço Corten Microtextura Fosco', 'Poliéster', 'Microtextura Fosco', 'M-3913', 4.20, 48.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Verde Brinquedo'), 'Verde Agua', 'Híbrido', null, 'BT982271036', 31.50, 54.05),  -- preço inventado
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Rosa Brinquedo'), 'Lilás Liso Brilhante Craquelado', 'Liso', 'Brilhante', 'PBY025 - Craquelado', 14.20, 54.39),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Amarelo Brinquedo'), 'Amarelo Ral 1003 BR', null, null, null, 31.20, 21.00),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Cinza N6,5 Texturizado'), 'Cinza N6,5 Texturizado', 'Poliéster', 'Brilhante', 'C-3929', 3.00, 45.32),
    ((select codigo from Pessoas where nome = 'WEG Tintas'), (select codigo from Cores where nome = 'Marrom Mesclado'), 'Politherm 46 WR R MT Marrom Mediterraneo Mesclado', 'Poliéster', null, '10888950', 25.00, 27.05);  -- preço inventado

-- ---------------------------------------------------------------------------------------------
-- 6. Ordens de serviço: 12 OSs reais da planilha (número, cliente, itens e valores), adaptadas ao banco do sistema.
--    Status da planilha -> status do sistema: Entregue/Concluída -> Concluída; Em Produção -> Em produção; Pendente -> Criada;
--    Pausada e Cancelada iguais. valor_os = soma dos itens não cancelados. prioridade = prioridade do cliente pelo
--    faturamento (0 a 100, regra de 22/10) sobre as OSs desta carga. Quem abriu a OS: um dos administradores do grupo.
-- ---------------------------------------------------------------------------------------------
set identity_insert OrdensDeServico on;
insert into OrdensDeServico (nr_os, cliente_codigo, usuario_codigo, data_entrada, valor_os, prioridade) values
    (1, (select codigo from Pessoas where nome = 'Brazil Metais'), (select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-09-25T10:12:00', 3744.00, 100),
    (2, (select codigo from Pessoas where nome = 'Ladeia'), (select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-02T08:48:00', 366.00, 10),
    (3, (select codigo from Pessoas where nome = 'Serralheria Primavera'), (select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-06T15:12:00', 200.00, 5),
    (4, (select codigo from Pessoas where nome = 'Expotec'), (select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-16T08:41:00', 600.00, 32),
    (5, (select codigo from Pessoas where nome = 'Fernando Alvez'), (select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-07T08:05:00', 650.00, 17),
    (6, (select codigo from Pessoas where nome = 'Celso Bike'), (select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-07T09:12:00', 180.00, 5),
    (7, (select codigo from Pessoas where nome = 'SiloKing'), (select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-05T13:48:00', 580.00, 15),
    (8, (select codigo from Pessoas where nome = 'Cesar Belarmino'), (select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-15T15:12:00', 150.00, 4),
    (9, (select codigo from Pessoas where nome = 'World Games'), (select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-06T09:33:00', 1440.00, 38),
    (10, (select codigo from Pessoas where nome = 'Savio'), (select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-08T08:05:00', 210.00, 6),
    (11, (select codigo from Pessoas where nome = 'Fabrício'), (select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-07T14:41:00', 0.00, 0),
    (12, (select codigo from Pessoas where nome = 'Expotec'), (select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-07T10:33:00', 600.00, 32);
set identity_insert OrdensDeServico off;

-- ---------------------------------------------------------------------------------------------
-- 7. Itens das OSs, com fotos, etapas (ItensRotasProcessos) e histórico de status de cada item.
--    Rota: Jato -> Jateamento; Jato e Pintura/Reforma -> Jateamento, Pintura; Banho | Pintura -> Banho, Pintura; Pintura -> Pintura.
--    ('Pintura | Jato' da planilha foi ordenado como Jateamento, Pintura.) Cor/tinta só nos itens com Pintura; a tinta é uma
--    das cadastradas com a mesma cor. Operador: André (Jateamento, Banho) e Richard (Pintura). Datas das etapas inventadas
--    a partir da entrada e da finalização da planilha, só em dias úteis (12/10 é feriado).
-- ---------------------------------------------------------------------------------------------

-- OS 1 (OS 859 na planilha) - Brazil Metais - Concluída
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (1, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Guarda Corpo quadrado peq.', 48.00, 'PEÇA', '2026-09-25T10:12:00', '2026-10-02', 1872.00, '2026-10-01T16:30:00', '2919');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '7f6a4e19-f48a-59d6-a45e-8269ec984622.jpg'), (@item, '242d7367-f623-5e80-b74a-9271831f1851.jpg'), (@item, '34237d31-3c38-5dba-a413-11ac1b667a51.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-09-30T08:00:00', '2026-09-30T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('859A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-01T07:40:00', '2026-10-01T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('859A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-09-25T10:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-09-25T10:13:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-01T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (1, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Guarda Corpo quadrado peq.', 48.00, 'PEÇA', '2026-09-25T10:12:00', '2026-10-02', 1872.00, '2026-10-13T16:30:00', '2936');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '1a949e92-e7b3-57d4-8100-383f4d9b6833.jpg'), (@item, '91758465-ca0f-57e0-bfc7-659616cb2da1.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-09T08:00:00', '2026-10-09T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('859B-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-13T07:40:00', '2026-10-13T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('859B-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-09-25T10:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-09-25T10:13:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-13T16:30:00');

-- OS 2 (OS 877 na planilha) - Ladeia - Concluída
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (2, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Labamba', 1.00, 'PEÇA', '2026-10-02T08:48:00', '2026-10-09', 270.00, '2026-10-21T16:30:00', '2961');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '9c82010c-cec4-5843-9c47-8ce9f1c5fd57.jpg'), (@item, '55f7ba50-1c7d-59c9-b3db-3a9d5f37f6a0.jpg'), (@item, 'a179f5a2-6fed-54cc-9b45-e326960ad72e.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-20T08:00:00', '2026-10-20T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('877A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-21T07:40:00', '2026-10-21T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('877A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-02T08:48:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-02T08:49:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-21T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (2, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Caixinhas', 48.00, 'PEÇA', '2026-10-02T08:48:00', '2026-10-09', 96.00, '2026-10-16T16:30:00', '2943');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'aa8ff56e-461a-5f39-ab01-678a5c4c5796.jpg'), (@item, '86fa0e54-7b19-5517-aceb-2b06ba1343d6.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-15T08:00:00', '2026-10-15T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('877B-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-16T07:40:00', '2026-10-16T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('877B-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-02T08:48:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-02T08:49:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-16T16:30:00');

-- OS 3 (OS 885 na planilha) - Serralheria Primavera - Concluída
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (3, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Corrimões', 2.00, 'PEÇA', '2026-10-06T15:12:00', '2026-10-13', 200.00, '2026-10-20T16:30:00', '2956');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '79f99001-4bf6-5daf-8a79-644b71df40b7.jpg'), (@item, '726a45ee-3dcd-5a9d-a1be-3a0461abc33a.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-19T08:00:00', '2026-10-19T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('885A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-20T07:40:00', '2026-10-20T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('885A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-06T15:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-06T15:13:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-20T16:30:00');

-- OS 4 (OS 904 na planilha) - Expotec - Concluída
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (4, (select codigo from Tintas where descricao = 'Branco Brilhante'), (select codigo from Cores where nome = 'Branco Total'), 'Kit Repositor', 100.00, 'CONJUNTO', '2026-10-16T08:41:00', '2026-10-22', 600.00, '2026-10-20T16:30:00', '2948');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '19d3d30f-1c01-5273-acef-4b9239f04eaa.jpg'), (@item, '7ff572a9-fe4d-521c-b127-8eb6e47d4f97.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Banho'), @item, 1, '2026-10-19T08:00:00', '2026-10-19T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('904A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-20T07:40:00', '2026-10-20T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('904A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-16T08:41:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-16T08:42:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-20T16:30:00');

-- OS 5 (OS 887 na planilha) - Fernando Alvez - Em produção
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (5, (select codigo from Tintas where descricao = 'Branco Brilhante'), (select codigo from Cores where nome = 'Branco Total'), 'Suporte Retangular', 4.00, 'PEÇA', '2026-10-07T08:05:00', '2026-10-14', 90.00, '2026-10-20T16:30:00', '2954');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'e020ede3-1fde-55c6-9ca1-94295d2755e3.jpg'), (@item, 'b20e80ff-868d-58b4-aa48-9adaa5fa37f2.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-19T08:00:00', '2026-10-19T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-20T07:40:00', '2026-10-20T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T08:06:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-20T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (5, (select codigo from Tintas where descricao = 'Preto Poliester Fosco'), (select codigo from Cores where nome = 'Preto Fosco'), 'Pé de Mesa Jardim', 6.00, 'PEÇA', '2026-10-07T08:05:00', '2026-10-14', 300.00, '2026-10-19T16:30:00', '2946');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '781ce55c-ae42-5984-8022-c720b714e57a.jpg'), (@item, '1c52e90c-32fb-58b0-9706-92465a32816f.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-16T08:00:00', '2026-10-16T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887B-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-19T07:40:00', '2026-10-19T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887B-2', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 3, '2026-10-21T13:00:00', null, (select codigo from Pessoas where nome = 'Richard')); -- retrabalho (Pintura repetida; o item continua Executada)
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887B-3', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T08:06:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-19T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (5, (select codigo from Tintas where descricao = 'Marrom Ral 8014'), (select codigo from Cores where nome = 'Marrom Ladeia'), 'Metalões', 15.00, 'PEÇA', '2026-10-07T08:05:00', '2026-10-14', 120.00, null, null);  -- inventado: valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'cc1ef12b-143d-5556-94bf-603be930ca09.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-20T08:05:00', '2026-10-20T11:00:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887C-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-21T07:45:00', null, (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887C-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T08:06:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (5, (select codigo from Tintas where descricao = 'Preto Rio Preto Microtextura'), (select codigo from Cores where nome = 'Preto Microtextura'), 'Suporte Bancada', 1.00, 'PEÇA', '2026-10-07T08:05:00', '2026-10-14', 60.00, '2026-10-19T16:30:00', '2946');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'a1f1c1b3-7b2c-51e7-b432-a0fd183ba8d9.jpg'), (@item, '9c0d77dc-eb8a-52ed-9004-8eb0a62a8e42.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-16T08:00:00', '2026-10-16T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887D-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-19T07:40:00', '2026-10-19T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887D-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T08:06:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-19T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (5, (select codigo from Tintas where descricao = 'Preto Rio Preto Microtextura'), (select codigo from Cores where nome = 'Preto Microtextura'), 'Suporte Cruz', 1.00, 'PEÇA', '2026-10-07T08:05:00', '2026-10-14', 40.00, '2026-10-19T16:30:00', '2946');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '82f106fb-44f6-5354-9dfd-08d40e0b037b.jpg'), (@item, '010d8d7a-5246-5a0c-95fe-7ac58a526f77.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-16T08:00:00', '2026-10-16T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887E-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-19T07:40:00', '2026-10-19T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887E-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T08:06:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-19T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (5, (select codigo from Tintas where descricao = 'Preto Rio Preto Microtextura'), (select codigo from Cores where nome = 'Preto Microtextura'), 'Pé de mesa unilateral', 1.00, 'PEÇA', '2026-10-07T08:05:00', '2026-10-14', 40.00, '2026-10-19T16:30:00', '2946');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'ce0ab1e9-40ba-50b8-840a-18fd74fa8515.jpg'), (@item, 'c0d3e8fb-ad3a-5a1d-a6a8-a4682e2dcb22.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-16T08:00:00', '2026-10-16T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887F-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-19T07:40:00', '2026-10-19T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('887F-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T08:06:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-19T16:30:00');

-- OS 6 (OS 888 na planilha) - Celso Bike - Em produção
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (6, null, null, 'Quadro Bike', 1.00, 'PEÇA', '2026-10-07T09:12:00', '2026-10-12', 30.00, '2026-10-14T10:45:00', '2962');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '322c588a-eb37-593d-a0a6-0977d2f78237.jpg'), (@item, '0a5e396e-a98d-5430-a1c9-8e8d9c098848.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-14T08:05:00', '2026-10-14T10:45:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('888A-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T09:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T09:13:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-14T10:45:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (6, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Conj. Quadro Bike', 2.00, 'CONJUNTO', '2026-10-07T09:12:00', '2026-11-04', 90.00, null, '2962');  -- inventado: valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '20b28aaa-2f34-5842-9e6c-55d82b16f2fb.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-21T08:20:00', null, (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('888B-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('888B-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T09:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T09:13:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (6, (select codigo from Tintas where descricao = 'Vermelho Coca Cola'), (select codigo from Cores where nome = 'Vermelho Brinquedo'), 'Conj. Bike', 1.00, 'CONJUNTO', '2026-10-07T09:12:00', '2026-11-04', 60.00, null, '2962');  -- inventado: valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '21d9acfb-b238-5613-8fa5-e9209a01b21d.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('888C-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('888C-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T09:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-07T09:13:00');

-- OS 7 (OS 880 na planilha) - SiloKing - Em produção
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (7, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Caixas Média', 2.00, 'PEÇA', '2026-10-05T13:48:00', '2026-11-02', 400.00, '2026-10-13T16:30:00', '2951');
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '924de613-94e9-5448-ab6a-0c0f5c8cec07.jpg'), (@item, '57f809c9-04d4-590d-8192-86f731eee315.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-09T08:00:00', '2026-10-09T11:30:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('880A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-13T07:40:00', '2026-10-13T16:30:00', (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('880A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-05T13:48:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-05T13:49:00'),
    (@item, (select codigo from StatusItens where status = 'Executada'), '2026-10-13T16:30:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (7, (select codigo from Tintas where descricao = 'Vermelho Coca Cola'), (select codigo from Cores where nome = 'Vermelho Brinquedo'), 'Painel', 1.00, 'PEÇA', '2026-10-05T13:48:00', '2026-11-02', 180.00, null, null);  -- inventado: valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '6234db80-7326-52ec-b212-64c4f7dcf798.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-19T13:30:00', '2026-10-19T16:10:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('880B-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('880B-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-05T13:48:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-05T13:49:00');

-- OS 8 (OS 903 na planilha) - Cesar Belarmino - Em produção
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (8, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Argola 10', 50.00, 'PEÇA', '2026-10-15T15:12:00', '2026-10-20', 50.00, null, null);
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '7518781b-f2aa-541e-b1c0-849d5ff453dd.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 1, '2026-10-21T14:00:00', null, (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('903A-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-15T15:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-15T15:13:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (8, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Argola 13', 50.00, 'PEÇA', '2026-10-15T15:12:00', '2026-10-20', 50.00, null, null);
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'cca07557-9710-5ebe-ab58-a743d714d76d.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('903B-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-15T15:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-15T15:13:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (8, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Argola 15', 50.00, 'PEÇA', '2026-10-15T15:12:00', '2026-10-20', 50.00, null, null);
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'd97d38a4-e6e6-514b-8096-6bb333f860f9.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('903C-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-15T15:12:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-15T15:13:00');

-- OS 9 (OS 884 na planilha) - World Games - Em produção
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (9, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Portão 1900x1500', 30.00, 'PEÇA', '2026-10-06T09:33:00', '2026-10-13', 900.00, null, null);
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '3ad9e351-b03f-5fbb-9fa1-19e1e5195ad2.jpg'), (@item, 'c93290ce-b30e-5502-a875-82f2d2789d90.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-19T08:10:00', '2026-10-19T11:20:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, '2026-10-21T07:50:00', null, (select codigo from Pessoas where nome = 'Richard'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-06T09:33:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-06T09:34:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (9, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Portão 900x1500', 3.00, 'PEÇA', '2026-10-06T09:33:00', '2026-10-13', 60.00, null, null);
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'a508a7e7-5580-5e1b-9fc6-f5677d268196.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-21T08:00:00', '2026-10-21T10:40:00', (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884B-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884B-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-06T09:33:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-06T09:34:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (9, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Portão 600x1500', 3.00, 'PEÇA', '2026-10-06T09:33:00', '2026-10-13', 60.00, null, null);
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '378e47c8-8394-5a0b-81e6-a9f093fe13b4.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-21T10:50:00', null, (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884C-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884C-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-06T09:33:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-06T09:34:00');
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (9, (select codigo from Tintas where descricao = 'Preto Ral 9005'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Cilindros 1500x170', 35.00, 'PEÇA', '2026-10-06T09:33:00', '2026-10-13', 420.00, null, null);  -- inventado: valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'e441ca6d-4568-5531-80c6-6b9a2e57ed06.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884D-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('884D-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-06T09:33:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-06T09:34:00');

-- OS 10 (OS 204 na planilha) - Savio - Pausada
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (10, null, null, 'Brise (fechamento de janela)', 6.00, 'PEÇA', '2026-10-08T08:05:00', '2026-10-22', 210.00, null, null);  -- inventado: entrada, ose, prazo, qtd, valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'a2a92bc4-31c0-54a1-8dea-ea7f3188abe2.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, '2026-10-14T08:30:00', null, (select codigo from Pessoas where nome = 'André'));
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('204A-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-08T08:05:00'),
    (@item, (select codigo from StatusItens where status = 'Aguardando execução'), '2026-10-08T08:06:00'),
    (@item, (select codigo from StatusItens where status = 'Pausada'), '2026-10-16T09:31:00');

-- OS 11 (OS 889 na planilha) - Fabrício - Cancelada
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (11, null, (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Banco de Tomar Sol', 3.00, 'PEÇA', '2026-10-07T14:41:00', '2026-11-04', 150.00, null, null);  -- inventado: valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, 'e31b7569-6637-54bd-86fc-be9d14e72f3b.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Jateamento'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('889A-1', @etapa);
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 2, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('889A-2', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T14:41:00'),
    (@item, (select codigo from StatusItens where status = 'Cancelada'), '2026-10-09T10:21:00');

-- OS 12 (OS 890 na planilha) - Expotec - Criada
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (12, null, (select codigo from Cores where nome = 'Branco Total'), 'Kit Repositores', 100.00, 'CONJUNTO', '2026-10-07T10:33:00', '2026-10-12', 600.00, null, null);  -- inventado: cor, qtd, valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '2345fb18-2d3b-57ce-b494-c0a4626dec05.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('890A-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T10:33:00');

-- ---------------------------------------------------------------------------------------------
-- 8. Histórico de status das OSs. Criada = abertura da OS; Em produção = início da 1ª etapa (regra RN-OS-06);
--    Concluída = pouco depois da última etapa (todos os itens Executada); Pausada e Cancelada: datas inventadas.
-- ---------------------------------------------------------------------------------------------
insert into HistoricosStatusOs (os_codigo, status_codigo, data_hora) values
    (1, (select codigo from StatusOs where status = 'Criada'), '2026-09-25T10:12:00'),
    (1, (select codigo from StatusOs where status = 'Em produção'), '2026-09-30T08:00:00'),
    (1, (select codigo from StatusOs where status = 'Concluída'), '2026-10-13T16:42:00'),
    (2, (select codigo from StatusOs where status = 'Criada'), '2026-10-02T08:48:00'),
    (2, (select codigo from StatusOs where status = 'Em produção'), '2026-10-15T08:00:00'),
    (2, (select codigo from StatusOs where status = 'Concluída'), '2026-10-21T16:42:00'),
    (3, (select codigo from StatusOs where status = 'Criada'), '2026-10-06T15:12:00'),
    (3, (select codigo from StatusOs where status = 'Em produção'), '2026-10-19T08:00:00'),
    (3, (select codigo from StatusOs where status = 'Concluída'), '2026-10-20T16:42:00'),
    (4, (select codigo from StatusOs where status = 'Criada'), '2026-10-16T08:41:00'),
    (4, (select codigo from StatusOs where status = 'Em produção'), '2026-10-19T08:00:00'),
    (4, (select codigo from StatusOs where status = 'Concluída'), '2026-10-20T16:42:00'),
    (5, (select codigo from StatusOs where status = 'Criada'), '2026-10-07T08:05:00'),
    (5, (select codigo from StatusOs where status = 'Em produção'), '2026-10-16T08:00:00'),
    (6, (select codigo from StatusOs where status = 'Criada'), '2026-10-07T09:12:00'),
    (6, (select codigo from StatusOs where status = 'Em produção'), '2026-10-14T08:05:00'),
    (7, (select codigo from StatusOs where status = 'Criada'), '2026-10-05T13:48:00'),
    (7, (select codigo from StatusOs where status = 'Em produção'), '2026-10-09T08:00:00'),
    (8, (select codigo from StatusOs where status = 'Criada'), '2026-10-15T15:12:00'),
    (8, (select codigo from StatusOs where status = 'Em produção'), '2026-10-21T14:00:00'),
    (9, (select codigo from StatusOs where status = 'Criada'), '2026-10-06T09:33:00'),
    (9, (select codigo from StatusOs where status = 'Em produção'), '2026-10-19T08:10:00'),
    (10, (select codigo from StatusOs where status = 'Criada'), '2026-10-08T08:05:00'),
    (10, (select codigo from StatusOs where status = 'Em produção'), '2026-10-14T08:30:00'),
    (10, (select codigo from StatusOs where status = 'Pausada'), '2026-10-16T09:30:00'),
    (11, (select codigo from StatusOs where status = 'Criada'), '2026-10-07T14:41:00'),
    (11, (select codigo from StatusOs where status = 'Cancelada'), '2026-10-09T10:20:00'),
    (12, (select codigo from StatusOs where status = 'Criada'), '2026-10-07T10:33:00');

-- ---------------------------------------------------------------------------------------------
-- 9. Cronogramas (um por dia) e a fila de etapas de cada dia (CronogramasItens). Os dias passados repetem as etapas
--    que começaram naquele dia; hoje (22/10) e os próximos dias planejam o que ainda falta. A quantidade planejada de
--    uma etapa nunca passa da quantidade do item. pontuacao fica NULL (a fórmula de pontuação foi retirada).
-- ---------------------------------------------------------------------------------------------

-- Cronograma de 30/09/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-09-30', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '859A-1'), 48.00, 0);

-- Cronograma de 01/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-01', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '859A-2'), 48.00, 0);

-- Cronograma de 09/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-09', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '859B-1'), 48.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '880A-1'), 2.00, 1);

-- Cronograma de 13/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-13', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '859B-2'), 48.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '880A-2'), 2.00, 1);

-- Cronograma de 14/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-14', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '888A-1'), 1.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '204A-1'), 6.00, 1);

-- Cronograma de 15/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-15', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '877B-1'), 48.00, 0);

-- Cronograma de 16/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-16', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '877B-2'), 48.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '887B-1'), 6.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '887D-1'), 1.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '887E-1'), 1.00, 3),
    (@cronograma, (select codigo from @etapas where chave = '887F-1'), 1.00, 4);

-- Cronograma de 19/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-19', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '887B-2'), 6.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '887D-2'), 1.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '887E-2'), 1.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '887F-2'), 1.00, 3),
    (@cronograma, (select codigo from @etapas where chave = '885A-1'), 2.00, 4),
    (@cronograma, (select codigo from @etapas where chave = '904A-1'), 100.00, 5),
    (@cronograma, (select codigo from @etapas where chave = '887A-1'), 4.00, 6),
    (@cronograma, (select codigo from @etapas where chave = '884A-1'), 30.00, 7),
    (@cronograma, (select codigo from @etapas where chave = '880B-1'), 1.00, 8);

-- Cronograma de 20/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-20', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '885A-2'), 2.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '904A-2'), 100.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '887A-2'), 4.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '877A-1'), 1.00, 3),
    (@cronograma, (select codigo from @etapas where chave = '887C-1'), 15.00, 4);

-- Cronograma de 21/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-21', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '877A-2'), 1.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '887C-2'), 8.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '884A-2'), 15.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '884B-1'), 3.00, 3),
    (@cronograma, (select codigo from @etapas where chave = '888B-1'), 1.00, 4),
    (@cronograma, (select codigo from @etapas where chave = '884C-1'), 2.00, 5),
    (@cronograma, (select codigo from @etapas where chave = '903A-1'), 25.00, 6);

-- Cronograma de 22/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-22', 'Plano do dia: terminar as peças em andamento e começar as da fila.');
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '887C-2'), 7.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '884A-2'), 15.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '888B-1'), 1.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '884C-1'), 1.00, 3),
    (@cronograma, (select codigo from @etapas where chave = '903A-1'), 25.00, 4),
    (@cronograma, (select codigo from @etapas where chave = '903C-1'), 50.00, 5),
    (@cronograma, (select codigo from @etapas where chave = '884B-2'), 3.00, 6),
    (@cronograma, (select codigo from @etapas where chave = '884D-1'), 35.00, 7);

-- Cronograma de 23/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-23', 'Prioridade para as OSs com prazo mais curto.');
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '888C-1'), 1.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '880B-2'), 1.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '903B-1'), 50.00, 2);

-- Cronograma de 26/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-26', 'Fila planejada para a segunda-feira.');
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '888B-2'), 2.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '888C-2'), 1.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '884C-2'), 3.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '884D-2'), 35.00, 3);

commit transaction;
print 'Carga de dados concluída.';
end try
begin catch
    if @@trancount > 0 rollback transaction;
    throw;
end catch
go

-- =============================================================================================
-- Conferência (só leitura)
-- =============================================================================================
select 'FontesAquisicao' as tabela, count(*) as linhas from FontesAquisicao
union all select 'StatusItens', count(*) from StatusItens
union all select 'StatusOs', count(*) from StatusOs
union all select 'RotasProcessos', count(*) from RotasProcessos
union all select 'Cores', count(*) from Cores
union all select 'Enderecos', count(*) from Enderecos
union all select 'Pessoas', count(*) from Pessoas
union all select 'Funcionarios', count(*) from Funcionarios
union all select 'Usuarios', count(*) from Usuarios
union all select 'Fornecedores', count(*) from Fornecedores
union all select 'Clientes', count(*) from Clientes
union all select 'PessoasFisicas', count(*) from PessoasFisicas
union all select 'PessoasJuridicas', count(*) from PessoasJuridicas
union all select 'Tintas', count(*) from Tintas
union all select 'OrdensDeServico', count(*) from OrdensDeServico
union all select 'Itens', count(*) from Itens
union all select 'Fotos', count(*) from Fotos
union all select 'Cronogramas', count(*) from Cronogramas
union all select 'ItensRotasProcessos', count(*) from ItensRotasProcessos
union all select 'CronogramasItens', count(*) from CronogramasItens
union all select 'HistoricosStatusOs', count(*) from HistoricosStatusOs
union all select 'HistoricosStatusItens', count(*) from HistoricosStatusItens
;

-- Cada OS com o status atual (último do histórico), o valor gravado e a soma dos itens não cancelados (as duas colunas de valor devem ser iguais)
select o.nr_os, p.nome as cliente, so.status as status_os, o.valor_os,
       (select coalesce(sum(i.valor_total_item), 0) from Itens i
         where i.os_codigo = o.nr_os
           and (select top 1 si.status from HistoricosStatusItens h join StatusItens si on si.codigo = h.status_item_codigo
                 where h.item_codigo = i.codigo order by h.data_hora desc) <> 'Cancelada') as soma_itens,
       o.prioridade, (select count(*) from Itens i where i.os_codigo = o.nr_os) as itens
  from OrdensDeServico o
  join Pessoas p on p.codigo = o.cliente_codigo
  join StatusOs so on so.codigo = (select top 1 h.status_codigo from HistoricosStatusOs h where h.os_codigo = o.nr_os order by h.data_hora desc)
 order by o.nr_os;

-- Itens por status atual
select si.status, count(*) as itens
  from Itens i
  join StatusItens si on si.codigo = (select top 1 h.status_item_codigo from HistoricosStatusItens h where h.item_codigo = i.codigo order by h.data_hora desc)
 group by si.status order by si.status;
