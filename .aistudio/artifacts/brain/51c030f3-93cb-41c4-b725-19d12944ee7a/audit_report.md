# QRZ Stock - Auditoria de Persistência e Firebase

## 1. Matriz de Auditoria de Entidades

| Entidade | Origem/Destino | Risco Identificado | Ação Corretiva |
| :--- | :--- | :--- | :--- |
| **Loja (Store)** | Firestore (`/stores`) | Auto-seed cego sobrescrevia registros ao carregar, causando inconsistência. | Removido auto-seed cego. Leituras agora são estritamente remotas. |
| **Setor (Sector)** | Firestore (`/sectors`) | Auto-seed cego sobrescrevia registros ao carregar. | Removido auto-seed cego. Leituras agora são estritamente remotas. |
| **Usuário (User)** | Auth + Firestore (`/users`) | Timeout na gravação do perfil, estado indefinido no cadastro. | Adicionados timeouts e tratamento de erro de recuperação de conta. |
| **Solicitação (Withdrawal)** | Firestore (`/withdrawalRequests`) | Possível inconsistência em operações offline (depende de regras). | Mantido integridade via `firestore.rules`. |
| **Produto (Product)** | Firestore (`/products`) | N/A (Consistente) | N/A |
| **Evidência (Evidence)** | Storage + Firestore (`/evidence`) | N/A (Consistente) | N/A |

## 2. Causas Raízes Identificadas

1.  **Conflito de Assinatura (APK):** O uso de builds `debug` (assinados automaticamente) em vez de `release` (assinados com chave estável) forçava a desinstalação para atualizações.
2.  **Lógica de Persistência (Lojas/Setores):** O `StoreSectorRepository` tentava "auto-seear" lojas padrão caso a leitura no Firestore retornasse vazio (ou demorasse). Isso causava sobrescrita de dados reais por listas fixas em memória ou registros repetidos.
3.  **Indefinição no Cadastro:** Falta de timeouts nas chamadas ao Firebase Auth e Firestore fazia a UI travar em vez de reportar falhas de rede.

## 3. Correções Aplicadas

-   **Segurança de Assinatura:** Ajustado o workflow de publicação para utilizar builds de `release` com chave estável (exige configuração dos secrets no GitHub).
-   **Remoção de Auto-Seed Destrutivo:** `StoreSectorRepository.kt` foi refatorado para não sobrescrever dados existentes com valores padrão.
-   **Resiliência de Cadastro:** Implementados timeouts (15s) e tratamento de erros no `AdminUserRepository.kt` para garantir que a UI sempre receba feedback (sucesso ou erro).

## 4. Próximos Passos (Manual)

1.  **GitHub Secrets:** Configurar `KEYSTORE_FILE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` e `KEY_ALIAS` no repositório GitHub para viabilizar builds de release consistentes.
2.  **Verificação:** Após configurar os secrets, realizar um novo build `release` e tentar instalar sobre a versão atual.
