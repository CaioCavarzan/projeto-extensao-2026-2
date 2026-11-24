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
insert into Cores (nome) values
    ('Amarelo Brinquedo'),
    ('Branco Total'),
    ('Laranja Brinquedo'),
    ('Marrom Ladeia'),
    ('Preto Fosco'),
    ('Preto Microtextura'),
    ('Preto Semi Brilho'),
    ('Rosa Brinquedo'),
    ('Verde Brinquedo'),
    ('Vermelho Brinquedo');

-- ---------------------------------------------------------------------------------------------
-- 2. Funcionários e usuários
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
-- 3. Fornecedores
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

insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015943', 'Avenida Duque de Caxias', '4192');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('FlexTintas', 'comercial.flextintas@example.com', '17945535103', @endereco);
set @pessoa = scope_identity();
insert into Fornecedores (pessoa_codigo, cnpj, ativo, prazo_pagamento) values (@pessoa, '80361052000122', 1, '30 dias');

-- ---------------------------------------------------------------------------------------------
-- 4. Clientes
-- ---------------------------------------------------------------------------------------------
-- Vai e Vem 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15062610', 'Rua Flavio Bianchini', '8445');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('VAI-VEM INDUSTRIA E COMERCIO DE BRINQUEDOS LTDA - EPP', 'compras.vai.e.vem@example.com', '17997232028', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Vai e Vem. Contato: Beto. Referência: Brinquedos Coloridos. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '66014614000130', 'Play Rio', '647090093110');
-- BSC Metal 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025409', 'Avenida Juscelino Kubitschek de Oliveira', '1815');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('BSCMETAL PEÇAS PARA MOTOS LTDA', 'administrativo@bscmetal.com.br', '11913766124', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Instagram'), 'Nome usual: BSC Metal. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '43878052000178', 'BSC Metal', '473030561116');
-- Brazil Metais 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15055383', 'Avenida Pedro Maria Gonçalves', '588');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Brazil Metais', 'compras.brazil.metais@example.com', '17991790498', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Não informada'), 'Nome usual: Brazil Metais. Contato: Moisés. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '24006427000165', 'Brazil Metais', null);
-- Ladeia
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15050354', 'Avenida Bernardino Lopes', '996');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Ladeia', 'compras.ladeia@example.com', '17981995180', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Ladeia. Contato: Wilson. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '80818643000186', 'Ladeia', '648216005818');
-- Serralheria Primavera
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15025727', 'Avenida Rio Branco', '152');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Serralheria Primavera', 'compras.serralheria.primavera@example.com', '17996282417', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Serralheria Primavera. Contato: Pedro. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '71575655000136', 'Serralheria Primavera', null);
-- Expotec
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15055366', 'Avenida Mirassol', '12');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Expotec', 'compras.expotec@example.com', '17935139839', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Expotec. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '16589834000114', 'Expotec', '364536894548');
-- Fernando Alvez
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15047124', 'Avenida Nelson Cardoso Gonçalves', '1159');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Fernando Alvez', 'compras.fernando.alvez@example.com', '17981004664', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Fernando Alvez. Contato: Fernando. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '42364687276', null);
-- Celso Bike
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15010956', 'Rua Eduardo Fonseca', '265');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Celso Bike', 'compras.celso.bike@example.com', '17909224834', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Instagram'), 'Nome usual: Celso Bike. Contato: Celso. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '67302899452', null);
-- SiloKing 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015687', 'Rua Voluntários de São Paulo', '447');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('SiloKing', 'compras.siloking@example.com', '17966916386', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: SiloKing. Contato: Gustavo. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '17031517000140', 'SiloKing', '383028542290');
-- Cesar Belarmino 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015708', 'Rua Pedro Amaral', '2439');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Cesar Belarmino', 'compras.cesar.belarmino@example.com', '17997736491', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Não informada'), 'Nome usual: Cesar Belarmino. Contato: Cesar Belarmino. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '95883479000168', 'Cesar Belarmino', null);
-- World Games
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015929', 'Avenida Bady Bassitt Norte', '331');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('World Games', 'compras.world.games@example.com', '17972262432', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Facebook'), 'Nome usual: World Games. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '14975119000194', 'World Games', null);
-- Savio 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15020326', 'Rua Castro Alves', '1394');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Savio', 'compras.savio@example.com', '17992323302', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Anúncio Pago'), 'Nome usual: Savio. Contato: Savio. Referência: Savio Vilela. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '92848109149', null);
-- Fabrício
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015539', 'Rua General Glicério', '879');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Fabrício', 'compras.fabricio@example.com', '17991911620', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Fabrício. Contato: Fabrício. Modelo financeiro: Título.');
insert into PessoasFisicas (cliente_codigo, cpf, nome_social) values (@pessoa, '30325319189', null);
-- Sinual 
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15060144', 'Avenida Romeu Strazzi', '889');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Sinual', 'compras.sinual@example.com', '17991420500', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Indicação'), 'Nome usual: Sinual. Contato: Emília. Modelo financeiro: Título.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '19161414000120', 'Sinual', null);
-- Lipa
insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel) values ('SP', 'São José do Rio Preto', '15015077', 'Avenida Bady Bassitt', '2813');
set @endereco = scope_identity();
insert into Pessoas (nome, email, telefone, endereco_codigo) values ('Lipa', 'compras.lipa@example.com', '17991294541', @endereco);
set @pessoa = scope_identity();
insert into Clientes (pessoa_codigo, fonte_codigo, observacao) values (@pessoa, (select codigo from FontesAquisicao where descricao = 'Google'), 'Nome usual: Lipa. Contato: Lipa. Modelo financeiro: Conta Corrente.');
insert into PessoasJuridicas (cliente_codigo, cnpj, nome_fantasia, ie) values (@pessoa, '14460234000126', 'Lipa', null);

-- ---------------------------------------------------------------------------------------------
-- 5. Tintas
-- ---------------------------------------------------------------------------------------------
insert into Tintas (fornecedor_codigo, cor_codigo, descricao, tipo, acabamento, ref_fornecedor, estoque_atual, valor_unit) values
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto Semi Brilho Poliester', 'Poliéster', 'Brilhante', 'P-3304', 0.00, 35.20),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Rosa Brinquedo'), 'Lilás Liso Brilhante', 'Liso', 'Brilhante', 'PBY025', 1.20, 54.39),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Vermelho Brinquedo'), 'Vermelho Coca Cola', 'Poliéster TGIC/BPA FREE', null, 'BT972309736', 33.00, 32.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Amarelo Brinquedo'), 'Amarelo PYB8', 'Poliéster', 'Liso', 'PYB8', 0.50, 30.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Laranja Brinquedo'), 'Laranja 2004', 'Poliéster', 'Liso', '2004', 1.00, 30.00),
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Verde Brinquedo'), 'Verde Liso Brilhante', 'Poliéster', 'Brilhante', 'PBK112', 2.20, 30.80),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Branco Total'), 'Branco Hibrido Brilhante Econ', 'Híbrido', 'Brilhante', 'B-1017e', 0.00, 26.52),
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Branco Total'), 'Branco Ral 9003', 'Poliéster', 'Liso Brilhante', 'Ral 9003', 0.00, 24.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Microtextura'), 'Preto Rio Preto Microtextura', 'Rio Preto', 'Microtextura', 'P-3915', 3.50, 42.89),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Fosco'), 'Preto Poliester Fosco', 'Poliéster', 'Fosco', 'P-3603', 2.00, 38.89),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto PPSB Ral 9005', 'Poliéster', 'Semi Brilho', 'PPSB', 0.00, 20.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Vermelho Brinquedo'), 'Vermelho Pant. 187C/Ral 3002 Pol. Liso Brilh', 'Poliéster', 'Liso Brilhante', 'H-3001', 0.00, 25.85),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Novar'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto Ral 9005', 'Poliéster', 'Liso Semi Brilho', 'Ral 9005', 0.00, 18.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Marrom Ladeia'), 'Marrom Ral 8014', 'Poliéster', 'Fosco', 'M-3600', 8.60, 44.00),
    ((select codigo from Pessoas where nome = 'Epristintas'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto Econômico Híbrido', 'Híbrido', 'Econômico', 'P-1304r', 25.00, 44.51),  -- preço inventado
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Preto Semi Brilho'), 'Preto N1 Semi Brilho', 'Poliéster', 'Semi Brilho', 'SB971500136', 24.50, 22.80),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Laranja Brinquedo'), 'Laranja Jacto L-3000', 'Poliéster', null, 'BT970936436', 20.00, 30.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Branco Total'), 'Branco Brilhante', 'Poliéster', 'Brilhante', 'BT970500736', 102.00, 28.00),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Verde Brinquedo'), 'Verde Agua', 'Híbrido', null, 'BT982271036', 31.50, 54.05),  -- preço inventado
    ((select codigo from Pessoas where nome = 'FlexTintas'), (select codigo from Cores where nome = 'Rosa Brinquedo'), 'Lilás Liso Brilhante Craquelado', 'Liso', 'Brilhante', 'PBY025 - Craquelado', 14.20, 54.39),
    ((select codigo from Pessoas where nome = 'Inove / Realsi'), (select codigo from Cores where nome = 'Amarelo Brinquedo'), 'Amarelo Ral 1003 BR', null, null, null, 31.20, 21.00);  -- preço inventado

-- ---------------------------------------------------------------------------------------------
-- 6. Ordens de serviço
-- ---------------------------------------------------------------------------------------------
set identity_insert OrdensDeServico on;
insert into OrdensDeServico (nr_os, cliente_codigo, usuario_codigo, data_entrada, valor_os, prioridade) values
    (1, (select codigo from Pessoas where nome = 'Brazil Metais'), (select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-09-25T10:12:00', 3744.00, 100),
    (2, (select codigo from Pessoas where nome = 'Ladeia'), (select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-02T08:48:00', 366.00, 10),
    (3, (select codigo from Pessoas where nome = 'Serralheria Primavera'), (select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-06T15:12:00', 200.00, 5),
    (4, (select codigo from Pessoas where nome = 'Expotec'), (select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-16T08:41:00', 600.00, 32),
    (5, (select codigo from Pessoas where nome = 'Fernando Alvez'), (select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-07T08:05:00', 480.00, 13),
    (6, (select codigo from Pessoas where nome = 'Celso Bike'), (select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-07T09:12:00', 90.00, 2),
    (7, (select codigo from Pessoas where nome = 'SiloKing'), (select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-05T13:48:00', 580.00, 15),
    (8, (select codigo from Pessoas where nome = 'Cesar Belarmino'), (select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-15T15:12:00', 100.00, 3),
    (9, (select codigo from Pessoas where nome = 'World Games'), (select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-06T09:33:00', 1320.00, 35),
    (10, (select codigo from Pessoas where nome = 'Savio'), (select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-08T08:05:00', 210.00, 6),
    (11, (select codigo from Pessoas where nome = 'Fabrício'), (select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-07T14:41:00', 0.00, 0),
    (12, (select codigo from Pessoas where nome = 'Expotec'), (select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-07T10:33:00', 600.00, 32);
set identity_insert OrdensDeServico off;

-- ---------------------------------------------------------------------------------------------
-- 7. Itens das OSs (20), com fotos, etapas (ItensRotasProcessos) e histórico de status de cada item.
-- ---------------------------------------------------------------------------------------------

-- OS 1 - Brazil Metais - Concluída
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

-- OS 2 - Ladeia - Concluída
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

-- OS 3 - Serralheria Primavera - Concluída
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

-- OS 4 - Expotec - Concluída
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

-- OS 5 - Fernando Alvez - Em produção
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

-- OS 6 - Celso Bike - Em produção
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

-- OS 7 - SiloKing - Em produção
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

-- OS 8 - Cesar Belarmino - Em produção
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

-- OS 9 - World Games - Em produção
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

-- OS 10 - Savio - Pausada
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

-- OS 11 - Fabrício - Cancelada
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

-- OS 12 - Expotec - Criada
insert into Itens (os_codigo, tinta_codigo, cor_codigo, descricao, qtd_total, unidade, data_entrada, prazo_entrega, valor_total_item, data_finalizacao, nr_ose) values
    (12, null, (select codigo from Cores where nome = 'Branco Total'), 'Kit Repositores', 100.00, 'CONJUNTO', '2026-10-07T10:33:00', '2026-10-12', 600.00, null, null);  -- inventado: cor, qtd, valor
set @item = scope_identity();
insert into Fotos (item_codigo, foto) values (@item, '2345fb18-2d3b-57ce-b494-c0a4626dec05.jpg');
insert into ItensRotasProcessos (rota_processo_codigo, item_codigo, ordem, data_inicio, data_conclusao, funcionario_codigo) values ((select codigo from RotasProcessos where nome = 'Pintura'), @item, 1, null, null, null);
set @etapa = scope_identity(); insert into @etapas (chave, codigo) values ('890A-1', @etapa);
insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values
    (@item, (select codigo from StatusItens where status = 'Pendente'), '2026-10-07T10:33:00');

-- ---------------------------------------------------------------------------------------------
-- 8. Histórico de status das OSs. Criada = abertura da OS; Em produção = início da 1ª etapa;
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
-- 9. Cronogramas (um por dia) e a fila de etapas de cada dia (CronogramasItens).
-- ---------------------------------------------------------------------------------------------

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
    (@cronograma, (select codigo from @etapas where chave = '887D-1'), 1.00, 2);

-- Cronograma de 19/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'goncalvesmarcio06@outlook.com'), '2026-10-19', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '887B-2'), 6.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '887D-2'), 1.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '885A-1'), 2.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '904A-1'), 100.00, 3),
    (@cronograma, (select codigo from @etapas where chave = '884A-1'), 30.00, 4),
    (@cronograma, (select codigo from @etapas where chave = '880B-1'), 1.00, 5);

-- Cronograma de 20/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'cardosopedrohenrique2006@gmail.com'), '2026-10-20', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '885A-2'), 2.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '904A-2'), 100.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '877A-1'), 1.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '887C-1'), 15.00, 3);

-- Cronograma de 21/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'caiocavarzan@gmail.com'), '2026-10-21', null);
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '877A-2'), 1.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '887C-2'), 8.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '884A-2'), 15.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '903A-1'), 25.00, 3);

-- Cronograma de 22/10/2026
insert into Cronogramas (usuario_codigo, data_planejada, observacao) values ((select funcionario_codigo from Usuarios where login = 'pexepi123@gmail.com'), '2026-10-22', 'Plano do dia: terminar as peças em andamento e começar as da fila.');
set @cronograma = scope_identity();
insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao) values
    (@cronograma, (select codigo from @etapas where chave = '887C-2'), 7.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '884A-2'), 15.00, 1),
    (@cronograma, (select codigo from @etapas where chave = '903A-1'), 25.00, 2),
    (@cronograma, (select codigo from @etapas where chave = '884D-1'), 35.00, 3);

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
    (@cronograma, (select codigo from @etapas where chave = '888C-2'), 1.00, 0),
    (@cronograma, (select codigo from @etapas where chave = '884D-2'), 35.00, 1);

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
-- Linhas por tabela: a coluna confere deve dar 'ok' nas 22 tabelas
select tabela, linhas, esperado, case when linhas = esperado then 'ok' else 'DIFERENTE' end as confere
  from (          select  1 as ordem, 'FontesAquisicao' as tabela, count(*) as linhas,  7 as esperado from FontesAquisicao
        union all select  2, 'StatusItens',           count(*),  5 from StatusItens
        union all select  3, 'StatusOs',              count(*),  5 from StatusOs
        union all select  4, 'RotasProcessos',        count(*),  3 from RotasProcessos
        union all select  5, 'Cores',                 count(*), 10 from Cores
        union all select  6, 'Enderecos',             count(*), 27 from Enderecos
        union all select  7, 'Pessoas',               count(*), 27 from Pessoas
        union all select  8, 'Funcionarios',          count(*),  8 from Funcionarios
        union all select  9, 'Usuarios',              count(*),  8 from Usuarios
        union all select 10, 'Fornecedores',          count(*),  4 from Fornecedores
        union all select 11, 'Clientes',              count(*), 15 from Clientes
        union all select 12, 'PessoasFisicas',        count(*),  4 from PessoasFisicas
        union all select 13, 'PessoasJuridicas',      count(*), 11 from PessoasJuridicas
        union all select 14, 'Tintas',                count(*), 21 from Tintas
        union all select 15, 'OrdensDeServico',       count(*), 12 from OrdensDeServico
        union all select 16, 'Itens',                 count(*), 20 from Itens
        union all select 17, 'Fotos',                 count(*), 33 from Fotos
        union all select 18, 'Cronogramas',           count(*),  8 from Cronogramas
        union all select 19, 'ItensRotasProcessos',   count(*), 36 from ItensRotasProcessos
        union all select 20, 'CronogramasItens',      count(*), 27 from CronogramasItens
        union all select 21, 'HistoricosStatusOs',    count(*), 28 from HistoricosStatusOs
        union all select 22, 'HistoricosStatusItens', count(*), 50 from HistoricosStatusItens
       ) as t
 order by ordem;

-- Coerência da carga reduzida: encontrado deve ser igual a esperado em todas as linhas
select verificacao, encontrado, esperado, case when encontrado = esperado then 'ok' else 'DIFERENTE' end as confere
  from (          select 1 as ordem, 'Cores usadas pelos itens' as verificacao,
                         (select count(distinct cor_codigo) from Itens) as encontrado, 6 as esperado
        union all select 2, 'Cores sem nenhuma tinta',
                         (select count(*) from Cores c where not exists (select 1 from Tintas t where t.cor_codigo = c.codigo)), 0
        union all select 3, 'Fornecedores sem nenhuma tinta',
                         (select count(*) from Fornecedores f where not exists (select 1 from Tintas t where t.fornecedor_codigo = f.pessoa_codigo)), 0
        union all select 4, 'Itens com cor diferente da cor da tinta',
                         (select count(*) from Itens i join Tintas t on t.codigo = i.tinta_codigo where t.cor_codigo <> i.cor_codigo), 0
        union all select 5, 'Clientes sem OS (Vai e Vem, BSC Metal, Sinual e Lipa)',
                         (select count(*) from Clientes c where not exists (select 1 from OrdensDeServico o where o.cliente_codigo = c.pessoa_codigo)), 4
        union all select 6, 'OS com valor diferente da soma dos itens não cancelados',
                         (select count(*) from OrdensDeServico o
                           where o.valor_os <> (select coalesce(sum(i.valor_total_item), 0) from Itens i
                                                 where i.os_codigo = o.nr_os
                                                   and (select top 1 si.status from HistoricosStatusItens h join StatusItens si on si.codigo = h.status_item_codigo
                                                         where h.item_codigo = i.codigo order by h.data_hora desc) <> 'Cancelada')), 0
        union all select 7, 'Cronogramas sem etapa',
                         (select count(*) from Cronogramas c where not exists (select 1 from CronogramasItens ci where ci.cronograma_codigo = c.codigo)), 0
        union all select 8, 'Etapas planejadas acima da quantidade do item',
                         (select count(*) from (select ci.item_rota_processo_codigo
                                                  from CronogramasItens ci
                                                  join ItensRotasProcessos e on e.codigo = ci.item_rota_processo_codigo
                                                  join Itens i on i.codigo = e.item_codigo
                                                 group by ci.item_rota_processo_codigo, i.qtd_total
                                                having sum(ci.qtd_planejado) > i.qtd_total) as x), 0
        union all select 9, 'OS com prioridade diferente da calculada pelo faturamento do cliente',
                         (select count(*) from OrdensDeServico o
                            join (select cliente_codigo, sum(valor_os) as faturamento from OrdensDeServico group by cliente_codigo) f
                              on f.cliente_codigo = o.cliente_codigo
                           where o.prioridade <> cast(round(100.0 * f.faturamento
                                 / nullif((select max(x.fat) from (select sum(valor_os) as fat from OrdensDeServico group by cliente_codigo) as x), 0), 0) as int)), 0
       ) as t
 order by ordem;

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