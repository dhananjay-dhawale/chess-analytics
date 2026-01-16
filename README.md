# Chess Analytics

Personal chess analytics web application for analyzing PGN files from Chess.com, Lichess, and other platforms.

## Features

- Upload large PGN files (10-30k games)
- Support for multiple chess accounts
- Calendar heatmap visualization
- Win/loss/draw statistics
- Filter by time control, color, account

## Tech Stack

**Backend:**
- Java 21
- Spring Boot 3.2
- Spring Data JPA
- SQLite

**Frontend:**
- React + Vite
- TypeScript

## Project Structure

```
chess-analytics/
├── backend/
│   └── src/main/java/com/chessanalytics/
│       ├── controller/     # REST endpoints
│       ├── service/        # Business logic
│       ├── parser/         # PGN parsing
│       ├── repository/     # Data access
│       ├── model/          # Entities & enums
│       ├── dto/            # Request/Response objects
│       └── config/         # Configuration
├── frontend/               # React application
└── data/
    ├── uploads/            # Stored PGN files
    └── chess.db            # SQLite database
```

## Quick Start

### Prerequisites

- Java 21
- Maven 3.8+
- Node.js 18+ (for frontend)

### Running the Backend

```bash
cd backend
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

### Database

SQLite database is created automatically at `data/chess.db` on first run.

## API Reference

### Accounts

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/accounts` | Create account |
| GET | `/api/accounts` | List all accounts |
| GET | `/api/accounts/{id}` | Get account |
| DELETE | `/api/accounts/{id}` | Delete account |

**Create Account Request:**
```json
{
  "platform": "CHESS_COM",
  "username": "your_username"
}
```

Platforms: `CHESS_COM`, `LICHESS`, `OTHER`

### Upload Games

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/accounts/{id}/upload` | Upload PGN file |
| GET | `/api/accounts/{id}/jobs/{jobId}` | Check upload status |

**Upload Request:**
```bash
curl -X POST http://localhost:8080/api/accounts/1/upload \
  -F "file=@games.pgn"
```

**Job Status Response:**
```json
{
  "id": 1,
  "accountId": 1,
  "status": "PROCESSING",
  "totalGames": 5000,
  "processedGames": 2500,
  "duplicateGames": 100,
  "progressPercent": 50
}
```

Status values: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`

### Analytics

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/analytics/calendar` | Games per day |
| GET | `/api/analytics/stats` | Win/loss statistics |

**Query Parameters:**
- `from` - Start date (YYYY-MM-DD, required for calendar)
- `to` - End date (YYYY-MM-DD, required for calendar)
- `accountId` - Filter by account
- `timeControl` - Filter by category: `BULLET`, `BLITZ`, `RAPID`, `CLASSICAL`
- `color` - Filter by color: `WHITE`, `BLACK`

**Calendar Response:**
```json
{
  "data": [
    { "date": "2024-01-15", "count": 12 },
    { "date": "2024-01-16", "count": 5 }
  ]
}
```

**Stats Response:**
```json
{
  "total": 1500,
  "wins": 750,
  "losses": 600,
  "draws": 150,
  "byColor": {
    "WHITE": { "wins": 400, "losses": 280, "draws": 70 },
    "BLACK": { "wins": 350, "losses": 320, "draws": 80 }
  }
}
```

## Troubleshooting

### "Database is locked" error
SQLite only allows one write at a time. If you're importing multiple files simultaneously, wait for each job to complete.

### Upload times out
Large files (30k+ games) may take several minutes to process. The upload endpoint returns immediately with a job ID - poll the job status endpoint for progress.

### Games not appearing
- Check that your username in the account matches the username in the PGN file (case-insensitive)
- Duplicate games are automatically skipped based on a hash of the game data

### Java version issues
Ensure you're running Java 21:
```bash
java -version
```

## Development Notes

- Async processing with `@Async` for large file uploads
- Streaming PGN parser for memory efficiency
- SHA-256 hash for duplicate detection
- Indexed queries for analytics performance
