import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Adds an HTTP Basic Authorization header to all calls hitting the Spring Boot
 * backend (/api/** and /process). Credentials live in sessionStorage and are
 * populated by the login dialog in AppComponent.
 *
 * For production, replace with an OAuth2 / OIDC redirect flow against Azure
 * Entra ID — the server becomes an oauth2-resource-server and this interceptor
 * injects a bearer token instead.
 */
export const basicAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const creds = sessionStorage.getItem('claims.basicAuth');
  if (!creds) {
    return next(req);
  }
  if (!req.url.startsWith('/api/') && !req.url.startsWith('/process')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: 'Basic ' + creds } }));
};
