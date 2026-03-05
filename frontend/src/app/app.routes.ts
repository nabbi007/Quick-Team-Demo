import { Routes } from '@angular/router';
import { PollListComponent } from './pages/poll-list.component';
import { LoginComponent } from './pages/login.component';
import { RegisterComponent } from './pages/register.component';
import { CreatePollComponent } from './pages/create-poll.component';
import { NotFoundComponent } from './pages/not-found.component';
import { authGuard } from './guards/auth.guard';
import { LayoutComponent } from './components/layout/auth-layout.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'polls' },
  {
    path: 'polls',
    canActivate: [authGuard],
    children: [
      { path: '', component: PollListComponent },
      { path: 'new', component: CreatePollComponent },
      { path: ':id', component: PollListComponent },
    ],
  },
  {
    path: 'auth',
    component: LayoutComponent,
    children: [
      { path: '', redirectTo: 'register', pathMatch: 'full' },
      { path: 'register', component: RegisterComponent },
      { path: 'login', component: LoginComponent },
    ],
  },
  { path: '**', component: NotFoundComponent },
];
