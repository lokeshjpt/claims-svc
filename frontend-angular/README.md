# Claims Processor — Angular Frontend

Standalone Angular 18 SPA that talks to the Spring Boot backend in this repo.

## Run

```bash
cd frontend-angular
npm install
npm start              # ng serve on http://localhost:4200 with proxy to :8080
```

The dev server proxies `/api/**` and `/process` to `http://localhost:8080`
(see `proxy.conf.json`), so **no CORS configuration is needed** as long as
the Spring Boot app is running on port 8080.

## Build for production

```bash
npm run build          # output in dist/claims-frontend
```

Drop the contents of `dist/claims-frontend/browser` behind any static host
(nginx, Azure Static Web Apps, etc.) and point it at the API.

## Test

```bash
npm test               # ng test (Karma + Jasmine)
```

## Project layout

| Path | What |
|---|---|
| `src/app/app.component.ts` | Standalone root component — REST tab + CSV upload tab |
| `src/app/services/claims.service.ts` | Typed HttpClient wrapper for `/api/v1/claims` + `/process` |
| `src/app/models/claim.ts` | TypeScript interfaces mirroring the Java DTOs |
| `proxy.conf.json` | Dev-server proxy → Spring Boot |
| `angular.json` | Single-project workspace, Bootstrap 5 + icons loaded as styles |
