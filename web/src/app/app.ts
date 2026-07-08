import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from './auth';
import { I18n } from './i18n';
import { Toast } from './toast';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly auth = inject(Auth);
  readonly toast = inject(Toast);
  readonly i18n = inject(I18n);

  logout(): void {
    this.auth.logout();
  }
}
