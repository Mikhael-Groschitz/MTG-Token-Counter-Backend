# TokenForge API

Backend REST em Spring Boot para o **MTG Token Counter** (TokenForge) — uma aplicação para criação, gerenciamento e contagem de tokens de Magic: The Gathering.

## Stack

- **Java 17** + **Spring Boot 3.4.3**
- **Spring Web** — API REST
- **Spring Data JPA** + **PostgreSQL** (hospedado no [Neon](https://neon.tech))
- **Spring Security** + **JWT** ([jjwt](https://github.com/jwtk/jjwt)) — autenticação stateless
- **Spring Mail** — verificação de conta e recuperação de senha por e-mail (SMTP Gmail)
- **Cloudinary** — armazenamento de imagens (avatar de usuário, anexos de bug report)
- **Lombok**
- **Maven**

## Estrutura do projeto

```
src/main/java/com/tokenforge/api/
├── config/          # Configurações gerais (tratamento global de exceções)
├── controllers/      # Endpoints REST (Auth, Token, BugReport)
├── dto/              # Objetos de request/response
├── entities/         # Entidades JPA (User, Token)
├── errorhandler/      # Modelo de resposta de erro
├── exceptions/        # Exceções de negócio
├── repositories/      # Repositórios Spring Data JPA
├── security/          # JWT, filtro de segurança e configuração do Spring Security
└── services/           # Regras de negócio (Auth, Token, BugReport, Cloudinary, Email)
```

## Pré-requisitos

- JDK 17+
- Maven 3.9+ (ou use o `mvnw`, se disponível)
- Uma instância PostgreSQL (ex.: [Neon](https://neon.tech))
- Conta Cloudinary
- Conta Gmail com [senha de app](https://myaccount.google.com/apppasswords) para envio de e-mails

## Configuração

Copie o arquivo de exemplo e preencha as variáveis de ambiente:

```bash
cp .env.example .env
```

| Variável | Descrição |
| --- | --- |
| `DB_URL` | URL JDBC do PostgreSQL |
| `DB_USERNAME` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco |
| `CLOUDINARY_CLOUD_NAME` | Nome da conta Cloudinary |
| `CLOUDINARY_API_KEY` | API key do Cloudinary |
| `CLOUDINARY_API_SECRET` | API secret do Cloudinary |
| `GOOGLE_CLIENT_ID` | Client ID usado na verificação do login com Google |
| `FRONTEND_URL` | Origem permitida no CORS (padrão do frontend) |
| `JWT_SECRET` | Segredo para assinatura dos tokens JWT (mín. 32 caracteres) |
| `GMAIL_USERNAME` | E-mail usado para envio de mensagens (verificação/recuperação) |
| `GMAIL_APP_PASSWORD` | Senha de app do Gmail |
| `BUG_REPORT_EMAIL` | (Opcional) destino dos reports de bug; usa `GMAIL_USERNAME` se vazio |

As variáveis são carregadas automaticamente pelo Spring a partir de `src/main/resources/application.properties`. Configure-as no seu ambiente, IDE, ou em um `.env` carregado pela sua ferramenta de execução preferida.

## Executando localmente

```bash
mvn clean install
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`, com todos os endpoints sob o context path `/api`.

### Docker

```bash
docker build -t tokenforge-api .
docker run -p 8080:8080 --env-file .env tokenforge-api
```

## Endpoints principais

Todos os endpoints estão prefixados com `/api`. Endpoints marcados com 🔒 exigem header `Authorization: Bearer <token>`.

### Autenticação (`/auth`)

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/auth/register` | Cria uma nova conta |
| POST | `/auth/login` | Autentica com e-mail e senha |
| POST | `/auth/google` | Autentica/registra via Google |
| POST | `/auth/verify-email` | Confirma o código de verificação enviado por e-mail |
| POST | `/auth/resend-verification` | Reenvia o código de verificação |
| POST | `/auth/forgot-password` | Solicita recuperação de senha |
| POST | `/auth/reset-password` | Redefine a senha com o token recebido por e-mail |
| PUT | `/auth/profile` 🔒 | Atualiza dados do perfil do usuário autenticado |

### Tokens (`/tokens`) 🔒

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/tokens` | Lista os tokens do usuário autenticado |
| POST | `/tokens` | Cria um novo token |
| PUT | `/tokens/{id}` | Atualiza um token existente |
| DELETE | `/tokens/{id}` | Remove um token |

### Bug report (`/bugs`)

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/bugs` | Envia um relatório de bug (com anexos opcionais) |

## Modelo de dados

- **User** — conta do usuário (e-mail/senha ou Google), com verificação de e-mail, avatar (Cloudinary) e fluxo de reset de senha.
- **Token** — token de Magic: The Gathering criado pelo usuário (nome, tipo, cor, identidade de cor, poder/resistência, habilidades, imagem, layout), associado a um `User`.

## Licença

Distribuído sob a licença [GPL-3.0](LICENSE).
