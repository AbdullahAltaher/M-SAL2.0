import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { isStandalone } from './app/core/pwa/device';

/**
 * The animated start in index.html fades out once Angular is up. In the installed app it plays
 * to the end (about 2.9s from launch, then a 0.9s fade); in a browser tab, or with "reduce
 * motion", it waits for nothing.
 */
function hideBoot(): void {
  const boot = document.getElementById('boot');
  if (!boot) return;
  const reduced = matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const minimum = isStandalone() && !reduced ? 2900 : 0;
  setTimeout(() => {
    boot.classList.add('out');
    setTimeout(() => boot.remove(), 1000);
  }, Math.max(0, minimum - performance.now()));
}

bootstrapApplication(App, appConfig)
  .then(hideBoot)
  .catch((err) => {
    console.error(err);
    hideBoot();
  });
