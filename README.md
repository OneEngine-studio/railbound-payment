# Railbound Payment Portal

The Railbound Battle Royale portal lets players top up their Real Coin balance through a mock checkout, buy in-game items, and view their inventory.

## Repository layout

| Directory | Contents |
| --- | --- |
| `frontend/` | Next.js player portal and admin console |
| `backend/` | Spring Boot API backed by MongoDB |
| `game-integration/` | Unity client scripts and Nakama integration |
| `infrastructure/` | Docker Compose configuration and PowerShell startup scripts |
| `tests/` | PowerShell acceptance check for the mock top-up and item purchase flow |

## Local service URLs

| Service | Default URL |
| --- | --- |
| Player portal | <http://localhost:3000> |
| Admin console | <http://localhost:3000/admin> |
| API base URL | <http://localhost:8080/api/v1> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
