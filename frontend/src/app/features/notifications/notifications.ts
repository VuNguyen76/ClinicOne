import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { AccountNav } from '../../shared/account-nav/account-nav';

@Component({
  selector: 'app-notifications',
  imports: [RouterLink, MatIconModule, AccountMenu, AccountNav],
  templateUrl: './notifications.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Notifications {}
