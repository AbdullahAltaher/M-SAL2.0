import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Theme } from './core/theme/theme';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {
  constructor() {
    // Light or dark from the first screen (sign-in included), not only once signed in
    inject(Theme);
  }
}
